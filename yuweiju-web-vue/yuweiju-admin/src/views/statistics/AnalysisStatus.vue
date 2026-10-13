<script setup lang="ts">
import type { AnalysisRun } from '../../api/modules/analysis'

defineProps<{ run: AnalysisRun | null; metrics: Record<string, unknown>; loading: boolean; error: string }>()
const statuses: Record<string, string> = { COMPLETE: '完成', PARTIAL: '部分成功', SKIPPED: '跳过', FAILED: '失败' }
const names: Record<string, string> = {
  snapshot: '快照', clustering: '分群', clusterCache: '分群缓存', heatTraining: '热度训练',
  validation: '离线验证', prediction: '预测', predictionCache: '预测缓存', training: '训练',
}
const reasons: Record<string, string> = {
  NO_USER_SNAPSHOTS: '无用户快照', NO_DISH_SNAPSHOTS: '无菜品快照', INSUFFICIENT_SAMPLES: '训练样本不足',
  NO_MODEL_BASELINE: '无模型，使用基线预测', XGBOOST_DISABLED: '热度训练已关闭', NO_NEW_MODEL: '本次未生成新模型',
  NO_EVAL_SAMPLES: '无验证样本', DEPENDENCY_FAILURE: '依赖或持久化失败', SNAPSHOT_FAILED: '快照失败',
  TRAINING_NOT_EXECUTED: '训练未执行，未运行预测',
}
const fields: Record<string, string> = {
  userSnapshotCount: '用户快照数', dishSnapshotCount: '菜品快照数', clusterRowCount: '分群记录数', clusterCount: '簇数',
  predictionCount: '预测条数', modelVersion: '模型版本', featureVersion: '特征版本', trainSampleCount: '训练样本数',
  evalSampleCount: '验证样本数', silhouette: '轮廓系数', silhouetteThreshold: '轮廓阈值', silhouettePassed: '分群效果通过',
  cachedUserCount: '用户缓存数', clusterKeyCount: '簇缓存数', modelLoaded: '已加载模型', snapshotDate: '模型训练日期', trainedAt: '模型保存时间',
}
function display(value: unknown) { return typeof value === 'boolean' ? value ? '通过 / 是' : '未通过 / 否' : String(value ?? '--') }
function effect(metrics: Record<string, unknown>) {
  return metrics.rmsePass === true && metrics.maePass === true ? '阈值通过（仍需业务验收）'
    : metrics.rmsePass === false || metrics.maePass === false ? '阈值未通过' : '尚无有效效果指标'
}
</script>

<template>
  <div class="analysis-status" aria-live="polite">
    <p v-if="loading">同步执行中，等待实际阶段结果…</p>
    <p v-if="error" class="failure">{{ error }}</p>
    <template v-if="run">
      <p>本次执行：{{ statuses[run.status] || run.status }} · {{ run.snapshotDate }} · 同步执行</p>
      <p>本次热度新训练：{{ run.heatModelTrained ? '已生成新模型' : '未确认新模型' }}</p>
      <div v-for="(stage, index) in run.stages" :key="`${stage.stage}-${index}`" class="stage">
        <strong>{{ names[stage.stage] || stage.stage }}：{{ statuses[stage.status] || stage.status }}</strong>
        <p>{{ stage.windowStart }} ~ {{ stage.windowEnd }}</p>
        <p v-if="stage.reason" class="failure">{{ reasons[stage.reason] || stage.reason }} ({{ stage.reason }})</p>
        <template v-for="(value, key) in stage.outputs" :key="key">
          <p v-if="fields[String(key)]">{{ fields[String(key)] }}：{{ display(value) }}</p>
        </template>
        <p v-if="stage.stage === 'validation' && stage.outputs.sampleCount">
          RMSE / MAE：{{ stage.outputs.rmse }} / {{ stage.outputs.mae }}；比例 {{ stage.outputs.rmseRatio }} / {{ stage.outputs.maeRatio }}；
          阈值 {{ stage.outputs.rmseThreshold }} / {{ stage.outputs.maeThreshold }}；{{ effect(stage.outputs) }}
        </p>
      </div>
    </template>
    <p>效果指标：{{ effect(metrics) }}。执行完成或业务 code1 不表示效果通过。</p>
    <p v-if="metrics.modelVersion">指标模型：{{ metrics.modelVersion }} · {{ metrics.snapshotDate }}；验证窗口 {{ metrics.evalWindowStart }} ~ {{ metrics.evalWindowEnd }}；样本 {{ metrics.sampleCount }}</p>
    <p v-if="metrics.rmse !== undefined">RMSE / MAE：{{ metrics.rmse }} / {{ metrics.mae }}；比例 {{ metrics.rmseRatio }} / {{ metrics.maeRatio }}；阈值 {{ metrics.rmseThreshold }} / {{ metrics.maeThreshold }}</p>
    <p>查询指标可能来自该日较早执行；以阶段中新模型版本核对本次效果。</p>
  </div>
</template>

<style scoped>
.analysis-status { color: #4a4137; font-size: 13px; overflow-wrap: anywhere; }
.stage { border-top: 1px solid #dfd4c5; padding: 10px 0; }
p { margin: 6px 0; }
.failure { color: #a53a27; }
</style>
