const fs = require('fs')
const path = require('path')

// Function to calculate relative path from file to assets/reference_images
function getRelativePath(filePath) {
  const srcDir = path.join(__dirname, '..', 'src')
  const relativeToSrc = path.relative(srcDir, path.dirname(filePath))
  const parts = relativeToSrc.split(path.sep).filter(p => p !== '')
  const upLevels = parts.length
  const upPath = '../'.repeat(upLevels)
  return upPath + 'assets/reference_images/'
}

// Get all .vue files in src directory
function findVueFiles(dir, files = []) {
  const items = fs.readdirSync(dir)
  for (const item of items) {
    const fullPath = path.join(dir, item)
    const stat = fs.statSync(fullPath)
    if (stat.isDirectory()) {
      findVueFiles(fullPath, files)
    } else if (item.endsWith('.vue')) {
      files.push(fullPath)
    }
  }
  return files
}

const srcDir = path.join(__dirname, '..', 'src')
const vueFiles = findVueFiles(srcDir)

let modifiedFiles = 0

for (const file of vueFiles) {
  let content = fs.readFileSync(file, 'utf8')
  let modified = false

  // Replace @refimg/ imports
  const refimgRegex = /@refimg\/([^'"]*)\?([^'"]*)/g
  content = content.replace(refimgRegex, (match, filename, query) => {
    const relativePath = getRelativePath(file)
    modified = true
    return `${relativePath}${filename}?${query}`
  })

  if (modified) {
    fs.writeFileSync(file, content, 'utf8')
    console.log('Modified:', path.relative(__dirname, file))
    modifiedFiles++
  }
}

console.log(`Modified ${modifiedFiles} files`)