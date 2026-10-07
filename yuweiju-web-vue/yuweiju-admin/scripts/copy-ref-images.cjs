const fs = require('fs')
const path = require('path')

// Usage: node scripts/copy-ref-images.cjs [sourceDir] [destinationDir]
const projectRoot = path.resolve(__dirname, '..')
const [sourceDir = path.join(projectRoot, '..', 'reference_images'),
  destinationDir = path.join(projectRoot, 'src', 'assets', 'reference_images')] = process.argv.slice(2)
const srcDir = path.resolve(sourceDir)
const destDir = path.resolve(destinationDir)

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
      process.exitCode = 1
    }
  }
}

console.log(`Copied ${copied} files from reference_images to src/assets/reference_images`)
