Component({
  properties: {
    dish: {
      type: Object,
      value: null,
    },
    added: {
      type: Boolean,
      value: false,
    },
  },
  data: {
    selectedFlavor: '',
    quantity: 1,
  },
  observers: {
    dish(dish) {
      if (!dish) return
      const flavors = Array.isArray(dish.flavors) ? dish.flavors : []
      this.setData({
        selectedFlavor: dish.selectedFlavor || (flavors.length ? flavors[0] : ''),
        quantity: dish.quantity && dish.quantity > 0 ? dish.quantity : 1,
      })
    },
  },
  methods: {
    onFlavorTap(e) {
      if (this.properties.added) return
      const flavor = e.currentTarget.dataset.flavor || ''
      this.setData({ selectedFlavor: flavor })
    },
    onMinusTap() {
      if (this.properties.added) return
      const quantity = Math.max(1, (this.data.quantity || 1) - 1)
      this.setData({ quantity })
    },
    onPlusTap() {
      if (this.properties.added) return
      const quantity = Math.min(10, (this.data.quantity || 1) + 1)
      this.setData({ quantity })
    },
    onAddTap() {
      if (this.properties.added) return
      this.triggerEvent('add', {
        dish: this.properties.dish,
        flavor: this.data.selectedFlavor || '',
        quantity: this.data.quantity || 1,
      })
    },
    onCancelTap() {
      if (this.properties.added) return
      this.triggerEvent('cancel', { dish: this.properties.dish })
    },
  },
})
