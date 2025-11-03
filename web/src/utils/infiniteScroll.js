(function (global, factory) {
  typeof exports === 'object' && typeof module !== 'undefined' ? module.exports = factory()
    : typeof define === 'function' && define.amd ? define(factory)
      : (global.infiniteScroll = factory())
}(this, function () {
  'use strict'

  var throttle = function throttle (fn, delay) {
    var now, lastExec, timer, context, args; //eslint-disable-line

    var execute = function execute () {
      fn.apply(context, args)
      lastExec = now
    }

    return function () {
      context = this
      args = arguments

      now = Date.now()

      if (timer) {
        clearTimeout(timer)
        timer = null
      }

      if (lastExec) {
        var diff = delay - (now - lastExec)
        if (diff < 0) {
          execute()
        } else {
          timer = setTimeout(function () {
            execute()
          }, diff)
        }
      } else {
        execute()
      }
    }
  }

  var getComputedStyle = document.defaultView.getComputedStyle
  var getClientHeight = function getClientHeight (element) {
    var currentNode = element
    // bugfix, see http://w3help.org/zh-cn/causes/SD9013 and http://stackoverflow.com/questions/17016740/onscroll-function-is-not-working-for-chrome
    while (currentNode && currentNode.tagName !== 'HTML' && currentNode.tagName !== 'BODY' && currentNode.nodeType === 1) {
      var overflowY = getComputedStyle(currentNode).overflowY
      if (overflowY === 'scroll' || overflowY === 'auto') {
        return currentNode
      }
      currentNode = currentNode.parentNode
    }
    return window
  }

  var getVisibleHeight = function getVisibleHeight (element) {
    if (element === window) {
      return document.documentElement.clientHeight
    }

    return element.clientHeight
  }

  var getElementTop = function getElementTop (element) {
    if (element === window) {
      return getScrollTop(window)
    }
    return element + getScrollTop(window)
  }

  let getScrollTop = function getScrollTop (element) {
    if (element === window) {
      return Math.max(
        window.pageYOffset || 0,
        document.documentElement.scrollTop
      )
    }

    return element.scrollTop
  }

  var isAttached = function isAttached (element) {
    var currentNode = element.parentNode
    while (currentNode) {
      if (currentNode.tagName === 'HTML') {
        return true
      }
      if (currentNode.nodeType === 11) {
        return false
      }
      currentNode = currentNode.parentNode
    }
    return false
  }

  var doCheck = function doCheck () {
    console.log('doCheck')
    var scrollEventTarget = this.scrollEventTarget
    var element = this.el
    var distance = this.distance

    var viewportScrollTop = getScrollTop(scrollEventTarget)
    var viewportBottom = viewportScrollTop + getVisibleHeight(scrollEventTarget)

    var shouldTrigger = false
    if (scrollEventTarget === element) {
      shouldTrigger = scrollEventTarget.scrollHeight - viewportBottom <= distance
    } else {
      var elementBottom =
        getElementTop(element) -
        getElementTop(scrollEventTarget) +
        element.offsetHeight +
        viewportScrollTop

      shouldTrigger = viewportBottom + distance >= elementBottom
    }

    if (shouldTrigger && this.expression) {
      this.expression()
    }
  }

  var doBind = function doBind () {
    console.log('doBind')
    if (this.binded) return; // eslint-disable-line
    this.binded = true

    var directive = this
    var element = directive.el

    var distanceExpr = element.getAttribute('infinite-scroll-distance')
    var distance = 0
    if (distanceExpr) {
      distance = Number(directive.vm[distanceExpr] || distanceExpr)
      if (isNaN(distance)) {
        distance = 0
      }
    }

    directive.scrollEventTarget = getClientHeight(element)
    directive.scrollListener = throttle(doCheck.bind(directive), directive.throttleDelay)
    directive.scrollEventTarget.addEventListener('scroll', directive.scrollListener)
    this.vm.$on('hook:beforeDestroy', function () {
      directive.scrollEventTarget.removeEventListener('scroll', directive.scrollListener)
    })

    var disabledExpr = element.getAttribute('infinite-scroll-disabled')
    var disabled = false
    console.log('disabledExpr:', disabledExpr)
    if (disabledExpr) {
      this.vm.$watch(disabledExpr, function (value) {
        directive.disabled = value
        if (!value) {
          console.log('doCheck', 'watch')
          doCheck.call(directive)
        }
      })
      disabled = Boolean(directive.vm[disabledExpr])
    }
    directive.disabled = disabled

    directive.distance = distance
    // doCheck.call(directive)
  }

  var InfiniteScroll = {
    bind: function (el, binding, vNode) {
      el['infinite'] = {
        el: el,
        vm: vNode.context,
        expression: binding.value
      }
      var args = arguments

      // el['infinite'].vm.$on('hook:mounted', () => {
      el['infinite'].vm.$nextTick(() => {
        if (isAttached(el)) {
          doBind.call(el['infinite'], args)
        }
        el['infinite'].bindTryCount = 0
        var tryBind = function tryBind () {
          if (el['infinite'].bindTryCount > 10) return; //eslint-disable-line
          el['infinite'].bindTryCount++
          if (isAttached(el)) {
            doBind.call(el['infinite'], args)
          } else {
            setTimeout(tryBind, 50)
          }
        }
        tryBind()
        // })
      })
    }
  }

  var install = function install (Vue) {
    Vue.directive('InfiniteScroll', InfiniteScroll)
  }

  if (window.Vue) {
    window.infiniteScroll = InfiniteScroll
    Vue.use(install); // eslint-disable-line
  }

  InfiniteScroll.install = install

  return InfiniteScroll
}))
