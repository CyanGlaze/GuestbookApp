const formatTime = date => {
  const year = date.getFullYear()
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours()
  const minute = date.getMinutes()
  const second = date.getSeconds()

  return `${[year, month, day].map(formatNumber).join('/')} ${[hour, minute, second].map(formatNumber).join(':')}`
}

const formatNumber = n => {
  n = n.toString()
  return n[1] ? n : `0${n}`
}

// 获取自定义导航栏尺寸（状态栏高度 + 导航栏高度），兼容新旧基础库
const getNavBarInfo = () => {
  const windowInfo = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()
  const statusBarHeight = windowInfo.statusBarHeight || 20
  let navBarHeight = 44
  try {
    const menu = wx.getMenuButtonBoundingClientRect()
    if (menu && menu.height) {
      navBarHeight = (menu.top - statusBarHeight) * 2 + menu.height
    }
  } catch (e) {}
  return {
    statusBarHeight,
    navBarHeight,
    totalHeight: statusBarHeight + navBarHeight
  }
}

module.exports = {
  formatTime,
  getNavBarInfo
}
