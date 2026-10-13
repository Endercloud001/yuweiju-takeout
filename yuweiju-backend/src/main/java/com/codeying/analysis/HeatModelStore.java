package com.codeying.analysis;

import com.codeying.exception.BusinessException;
import com.codeying.properties.AnalysisProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoost;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.Map;

/** Model artifacts and their metadata; no training dataset or business orchestration. */
@Component
public class HeatModelStore {
    private final AnalysisProperties properties;
    private final ObjectMapper json;
    public HeatModelStore(AnalysisProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }
    private Path directory() { return Path.of(properties.getXgboost().getModelDir()).toAbsolutePath().normalize(); }
    private Path metadataPath() { return directory().resolve("dish-heat-xgboost.meta.json"); }
    public synchronized Map<String, Object> metadata() {
        if (!Files.exists(metadataPath())) return Map.of();
        try {
            return json.readValue(Files.readString(metadataPath()), new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) { throw new BusinessException("热度模型元数据不可用", ex); }
    }
    public synchronized Loaded load() {
        Map<String, Object> meta = metadata();
        if (meta.isEmpty()) return null;
        Object version = meta.get("modelVersion");
        if (!(version instanceof String value) || value.isBlank()) throw new BusinessException("热度模型版本缺失");
        String file = meta.get("modelFile") instanceof String name ? name : "dish-heat-xgboost.json";
        Path path = directory().resolve(file).normalize();
        if (!path.getParent().equals(directory())) throw new BusinessException("热度模型路径无效");
        if (!Files.isRegularFile(path)) return null;
        if (!properties.getFeatureVersion().equals(meta.get("featureVersion"))) throw new BusinessException("热度模型特征版本不兼容");
        try { return new Loaded(XGBoost.loadModel(path.toString()), value, meta); }
        catch (Exception ex) { throw new BusinessException("热度模型加载失败", ex); }
    }
    /** Publish metadata only after a complete new version file; failed save retains the old version. */
    public synchronized void save(Booster booster, Map<String, Object> metadata) {
        Path temp = null;
        try {
            Files.createDirectories(directory());
            String file = metadata.get("modelVersion") + ".json";
            Path artifact = directory().resolve(file);
            booster.saveModel(artifact.toString());
            metadata.put("modelFile", file);
            temp = Files.createTempFile(directory(), "heat-meta-", ".json");
            Files.writeString(temp, json.writeValueAsString(metadata));
            Files.move(temp, metadataPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) { throw new BusinessException("热度模型保存失败，SQL 回滚不撤销文件副作用", ex); }
        finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); }
                catch (java.io.IOException ex) { /* Temporary file belongs to this model directory; preserve on cleanup failure. */ }
            }
        }
    }
    public record Loaded(Booster booster, String version, Map<String, Object> metadata) implements AutoCloseable {
        public void close() { booster.dispose(); }
    }
}
