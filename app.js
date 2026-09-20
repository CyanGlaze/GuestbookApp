App({
  onLaunch() {
    // 检查登录状态
    const userInfo = wx.getStorageSync('userInfo');
    if (!userInfo) {
      // 未登录，跳转到登录页
      wx.reLaunch({
        url: '/pages/login/login'
      });
    }
  },
  
  globalData: {
    userInfo: null,
    isLoggedIn: false
  }
});
