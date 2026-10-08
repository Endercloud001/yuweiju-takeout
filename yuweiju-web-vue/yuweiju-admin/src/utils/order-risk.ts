export function formatRiskLevel(level?: string | null): string {
  if (!level || level === 'UNAVAILABLE') return '风险暂不可用'
  return level
}
