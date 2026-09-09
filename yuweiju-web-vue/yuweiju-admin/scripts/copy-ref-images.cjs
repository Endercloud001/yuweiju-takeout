const fs = require('fs')
const path = require('path')

// Source: use absolute path to the external reference_images directory
const projectRoot = path.resolve(__dirname, '..')
const srcDir = path.resolve('E:/Learning Files/yuweiju-takeout/yuweiju-web-vue/reference_images')
const destDir = path.resolve(projectRoot, 'src', 'assets', 'reference_images')

console.log('Project root:', projectRoot)
console.log('Source directory:', srcDir)
console.log('Destination directory:', destDir)

if (!fs.existsSync(srcDir)) {
  console.error('Source directory does not exist:', srcDir)
  process.exit(2)
}

fs.mkdirSync(destDir, { recursive: true })

const entries = fs.readdirSync(srcDir, { withFileTypes: true })
let copied = 0
for (const entry of entries) {
  if (entry.isFile()) {
    const srcPath = path.join(srcDir, entry.name)
    const destPath = path.join(destDir, entry.name)
    try {
      fs.copyFileSync(srcPath, destPath)
      copied++
      console.log('Copied', entry.name)
    } catch (e) {
      console.error('Failed to copy', entry.name, e)
    }
  }
}

console.log(`Copied ${copied} files from reference_images to src/assets/reference_images`)
