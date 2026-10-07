const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { spawnSync } = require('node:child_process')

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'issue-4-refimg-'))
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))
  return root
}

function run(script, args, options = {}) {
  const result = spawnSync(process.execPath, [path.join(__dirname, script), ...args], {
    encoding: 'utf8',
    timeout: 10000,
    ...options,
  })
  assert.ifError(result.error)
  assert.equal(result.signal, null, result.stderr)
  return result
}

function write(file, content) {
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, content)
}

function read(file) {
  return fs.readFileSync(file, 'utf8')
}

test('copy top-level files, overwrite names, preserve unrelated files and repeat safely', t => {
  const root = fixture(t)
  const source = path.join(root, 'source')
  const target = path.join(root, 'target')
  write(path.join(source, 'one.png'), 'first image')
  write(path.join(source, 'two.png'), 'second image')
  write(path.join(source, 'nested', 'hidden.png'), 'not copied')
  write(path.join(target, 'one.png'), 'old image')
  write(path.join(target, 'unrelated.txt'), 'keep me')
  for (let attempt = 0; attempt < 2; attempt++) {
    const result = run('copy-ref-images.cjs', [source, target])
    assert.equal(result.status, 0, result.stderr)
    assert.equal(read(path.join(target, 'one.png')), 'first image')
    assert.equal(read(path.join(target, 'two.png')), 'second image')
    assert.equal(read(path.join(target, 'unrelated.txt')), 'keep me')
    assert.deepEqual(fs.readdirSync(target).sort(), ['one.png', 'two.png', 'unrelated.txt'])
  }
})

test('missing source exits nonzero without creating or changing the target', t => {
  const root = fixture(t)
  const source = path.join(root, 'missing')
  const target = path.join(root, 'target')
  let result = run('copy-ref-images.cjs', [source, target])
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /Source directory does not exist/)
  assert.equal(fs.existsSync(target), false)
  write(path.join(target, 'keep.txt'), 'unchanged')
  result = run('copy-ref-images.cjs', [source, target])
  assert.notEqual(result.status, 0)
  assert.deepEqual(fs.readdirSync(target), ['keep.txt'])
  assert.equal(read(path.join(target, 'keep.txt')), 'unchanged')
})

test('partial copy failure as non-root exits nonzero and preserves the failed target', t => {
  const root = fixture(t)
  const source = path.join(root, 'source')
  const target = path.join(root, 'target')
  write(path.join(source, 'blocked.png'), 'new blocked image')
  write(path.join(source, 'success.png'), 'copied image')
  write(path.join(target, 'blocked.png'), 'original blocked image')
  fs.chmodSync(root, 0o755)
  fs.chmodSync(target, 0o777)
  fs.chmodSync(path.join(target, 'blocked.png'), 0o444)
  // Root runners must drop privileges so permissions exercise a real copy failure.
  const options = process.getuid() === 0 ? { uid: 65534, gid: 65534 } : {}
  const result = run('copy-ref-images.cjs', [source, target], options)
  t.diagnostic(`copy subprocess exit=${result.status}; ${result.stderr.trim()}`)
  assert.match(result.stderr, /Failed to copy blocked.png/)
  assert.equal(read(path.join(target, 'success.png')), 'copied image')
  assert.equal(read(path.join(target, 'blocked.png')), 'original blocked image')
  assert.notEqual(result.status, 0, 'a partial copy failure must not report success')
})

test('convert queried imports at root and multiple depths, preserve other content and repeat safely', t => {
  const root = fixture(t)
  const source = path.join(root, 'src')
  const assets = path.join(source, 'images')
  const input = [
    "import image from '@refimg/one.png?url'",
    'import raw from "@refimg/nested/two.svg?raw&x=1"',
    "import plain from '@refimg/plain.png'",
    "import other from './other.png?url'",
  ].join('\n')
  const cases = [
    ['Root.vue', './images/'],
    ['views/Child.vue', '../images/'],
    ['views/deep/Child.vue', '../../images/'],
  ]
  for (const [name] of cases) write(path.join(source, name), input)
  const nonVue = path.join(source, 'unchanged.ts')
  write(nonVue, input)
  for (let attempt = 0; attempt < 2; attempt++) {
    const result = run('fix-refimg-imports.cjs', [source, assets])
    assert.equal(result.status, 0, result.stderr)
    for (const [name, prefix] of cases) {
      const expected = input.replace('@refimg/one.png?url', `${prefix}one.png?url`)
        .replace('@refimg/nested/two.svg?raw&x=1', `${prefix}nested/two.svg?raw&x=1`)
      assert.equal(read(path.join(source, name)), expected)
    }
    assert.equal(read(nonVue), input)
  }
})
