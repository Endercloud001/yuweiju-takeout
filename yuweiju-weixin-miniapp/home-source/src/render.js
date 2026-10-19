// Recovered native uni render for index.wxml. Keep $root bindings paired with the native template.
var render = function() {
  var _vm = this
  var _h = _vm.$createElement
  var _c = _vm._self._c || _h
  var l0 =
    _vm.dishListItems && _vm.dishListItems.length > 0
      ? _vm.__map(_vm.dishListItems, function(item, index) {
          var $orig = _vm.__get_orig(item)

          var g0 = item.price.toFixed(2)
          return {
            $orig: $orig,
            g0: g0
          }
        })
      : null
  var m0 = _vm.orderListData()
  var g1 = !(m0.length === 0) ? (_vm.cartReady && !_vm.cartInvalid ? _vm.orderDishPrice.toFixed(2) : '待恢复') : null

  var l2 = _vm.__map(_vm.moreNormdata, function(obj, index) {
    var $orig = _vm.__get_orig(obj)

    var l1 = _vm.__map(obj.value, function(item, ind) {
      var $orig = _vm.__get_orig(item)

      var g2 = _vm.flavorDataes.findIndex(function(it) {
        return item === it
      })
      return {
        $orig: $orig,
        g2: g2
      }
    })

    return {
      $orig: $orig,
      l1: l1
    }
  })

  var g3 = _vm.dishDetailes.type == 1 ? _vm.dishDetailes.price.toFixed(2) : null

  if (!_vm._isMounted) {
    _vm.e0 = function() {
      return (_vm.openOrderCartList = !_vm.openOrderCartList)
    }

    _vm.e1 = function() {
      return (_vm.openDetailPop = false)
    }

    _vm.e2 = function() {
      return (_vm.openDetailPop = false)
    }

    _vm.e3 = function($event) {
      _vm.openOrderCartList = !_vm.openOrderCartList
    }

    _vm.e4 = function($event) {
      $event.stopPropagation()
      _vm.openOrderCartList = _vm.openOrderCartList
    }
  }

  _vm.$mp.data = Object.assign(
    {},
    {
      $root: {
        l0: l0,
        m0: m0,
        g1: g1,
        l2: l2,
        g3: g3
      }
    }
  )
}
