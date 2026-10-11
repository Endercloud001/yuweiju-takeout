// Display-only state: never add/replace image fields on API rows or shared store records.
const fallback = '/static/history-dish-placeholder.png';
export default {
  data() { return { failedHistoricalImages: [] }; },
  created() { this._historicalImageRows = new WeakMap(); this._nextHistoricalImageRow = 0; },
  methods: {
    historicalImageRow(row) {
      const previous = this._historicalImageRows.get(row);
      if (!previous || previous.source !== row.image) this._historicalImageRows.set(row, { id: ++this._nextHistoricalImageRow, source: row.image });
      return this._historicalImageRows.get(row).id;
    },
    resetHistoricalImages() { this.failedHistoricalImages = []; this._historicalImageRows = new WeakMap(); },
    historicalImage(row) {
      const source = row.image;
      if (typeof source !== 'string' || !source.trim()) return fallback;
      return this.failedHistoricalImages.includes(this.historicalImageRow(row)) ? fallback : source;
    },
    historicalImageError(row, event) {
      const source = event.currentTarget.dataset.historySrc;
      // Ignore fallback errors and late events after a source changed; no retries/loops.
      if (String(event.currentTarget.dataset.historyRow) === String(this.historicalImageRow(row))
          && source !== fallback && source === row.image && this.historicalImage(row) !== fallback) {
        this.failedHistoricalImages.push(this.historicalImageRow(row));
      }
    }
  }
};
