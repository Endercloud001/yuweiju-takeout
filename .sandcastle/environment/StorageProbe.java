import com.codeying.utils.AliOssUtil;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class StorageProbe {
 public static void main(String[] args)throws Exception {
  var storage=new AliOssUtil("http://127.0.0.1:18082","sandbox-placeholder","sandbox-placeholder","sandbox-only","http://127.0.0.1:18082/sandbox-only",false,300);
  byte[] content="Synthetic SDK upload".getBytes(StandardCharsets.UTF_8);
  String url=storage.upload(content,"sdk-probe.png");
  if(!url.startsWith("http://127.0.0.1:18082/"))throw new AssertionError("isolated upload URL");
  if(!java.util.Arrays.equals(content,Files.readAllBytes(Path.of("/runtime/uploads/sdk-probe.png"))))throw new AssertionError("real SDK write not observed");
  System.out.println("ISOLATED_REAL_ALIOSS_SDK_UPLOAD_PASS; NO_REAL_OSS");
 }
}
