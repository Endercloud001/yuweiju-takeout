function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export function exportToCsv<T extends Record<string, unknown>>(rows: T[], filename: string) {
  const keys = Array.from(
    rows.reduce((set, row) => {
      Object.keys(row).forEach((k) => set.add(k))
      return set
    }, new Set<string>()),
  )

  const escape = (v: unknown) => {
    const s = v == null ? '' : String(v)
    const needsQuote = /[",\n\r\t]/.test(s)
    const escaped = s.replace(/"/g, '""')
    return needsQuote ? `"${escaped}"` : escaped
  }

  const header = keys.map(escape).join(',')
  const lines = rows.map((row) => keys.map((k) => escape(row[k])).join(','))
  const csv = [header, ...lines].join('\r\n')
  downloadBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), filename)
}

export function exportToExcelHtml(
  columns: Array<{ label: string; key: string }>,
  rows: Array<Record<string, unknown>>,
  filename: string,
) {
  const escapeHtml = (s: unknown) =>
    String(s ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')

  const thead = `<tr>${columns.map((c) => `<th>${escapeHtml(c.label)}</th>`).join('')}</tr>`
  const tbody = rows
    .map((r) => `<tr>${columns.map((c) => `<td>${escapeHtml(r[c.key])}</td>`).join('')}</tr>`)
    .join('')

  const html = `<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <style>
    table{border-collapse:collapse;font-family:Arial,"Microsoft YaHei",sans-serif;font-size:12px}
    th,td{border:1px solid #ddd;padding:6px 8px;white-space:nowrap}
    th{background:#f5f7fb}
  </style>
</head>
<body>
  <table>
    <thead>${thead}</thead>
    <tbody>${tbody}</tbody>
  </table>
</body>
</html>`

  downloadBlob(new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8' }), filename)
}
