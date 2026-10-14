export function formatRiskLevel(level?: string | null): string {
  if (!level || level === 'UNAVAILABLE') return '风险暂不可用'
  return level
}

export function formatRiskOutcome(status?: unknown, reason?: unknown): string {
  const states: Record<string, string> = {
    TRAINED: '本次已训练新模型并激活', REJECTED: '本次已训练新模型，未通过激活评估',
    SKIPPED: '本次训练已跳过', PARTIAL: '本次执行部分完成，模型未发布', FAILED: '本次训练失败',
    MODEL_MISSING: '模型不存在', MODEL_FILE_MISSING: '模型文件不存在', NO_METRICS: '模型无回放数据', INVALID_METRICS: '模型回放数据无法读取',
    AVAILABLE: '已有模型的离线回放指标', REPLAY_FAILED: '回放数据读取失败',
  }
  const reasons: Record<string, string> = {
    no_orders_in_window: '窗口内无订单', insufficient_dataset: '训练或回放样本不足',
    insufficient_positive_labels: '正样本不足', insufficient_classes: '训练样本缺少正例或负例', execution_failure: '执行失败，请查看服务端记录',
    pr_auc_regression: 'PR-AUC 下降', recall_regression: '召回率下降',
  }
  const state = typeof status === 'string' ? states[status] : undefined
  const detail = typeof reason === 'string' ? reasons[reason] : undefined
  return (state ?? '尚未确认本次训练结果') + (detail ? `：${detail}` : '')
}
