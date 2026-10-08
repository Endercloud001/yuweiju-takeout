from pathlib import Path
import zipfile
root=Path('/workspace');libs=root/'backend-libs';libs.mkdir(exist_ok=True)
with zipfile.ZipFile(root/'yuweiju-backend/target/proj-boot-1.0-SNAPSHOT.jar') as jar:
 for name in jar.namelist():
  if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
   (libs/Path(name).name).write_bytes(jar.read(name))
(root/'backend-classpath.txt').write_text(':'.join(str(p) for p in sorted(libs.glob('*.jar'))))
print('Prepared classpath from actual built Spring Boot jar:',len(list(libs.glob('*.jar'))),'libraries')
