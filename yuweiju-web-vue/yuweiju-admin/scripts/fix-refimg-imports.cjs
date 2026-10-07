const fs = require('fs')
const path = require('path')

// Usage: node scripts/fix-refimg-imports.cjs [scanRoot] [assetsDir]
const projectRoot = path.resolve(__dirname, '..')
const [scanRoot = path.join(projectRoot, 'src'),
  assetsDir = path.join(projectRoot, 'src', 'assets', 'reference_images')] = process.argv.slice(2)
const srcDir = path.resolve(scanRoot)
const targetDir = path.resolve(assetsDir)

function getRelativePath(filePath) {
  const relative = path.relative(path.dirname(filePath), targetDir).split(path.sep).join('/')
  const prefix = relative.startsWith('../') ? relative : `./${relative}`
  return prefix.endsWith('/') ? prefix : `${prefix}/`
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
