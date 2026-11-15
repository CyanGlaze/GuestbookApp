App({
  onLaunch() {
    // 不强制登录：核心功能（记礼金 / 导入 Excel / 统计）均为本地数据，无需登录即可使用。
    // 用户可在「我的」页主动点击登录以设置头像，避免触发「强制授权才能使用」的审核驳回。
    const userInfo = wx.getStorageSync('userInfo');
    if (userInfo) {
      this.globalData.userInfo = userInfo;
      this.globalData.isLoggedIn = true;
    }
  },

  globalData: {
    userInfo: null,
    isLoggedIn: false
  }
});
