Page({
  data: {
    tempAvatar: '',
    logging: false
  },

  onLoad() {
    // 检查是否已登录
    const userInfo = wx.getStorageSync('userInfo');
    if (userInfo) {
      this.goToBookshelf();
    }
  },

  // 点击「微信一键登录」→ 系统弹窗选头像 → 选完自动完成登录
  onChooseAvatar(e) {
    const { avatarUrl } = e.detail;
    this.setData({ tempAvatar: avatarUrl });
    this.finishLogin(avatarUrl);
  },

  // 只获取头像，昵称使用默认值（不收集昵称）
  finishLogin(avatarUrl) {
    if (this.data.logging) return;
    this.setData({ logging: true });

    const userInfo = {
      avatarUrl: avatarUrl || this.data.tempAvatar,
      nickName: '微信用户',
      loginTime: new Date().toISOString()
    };

    wx.setStorageSync('userInfo', userInfo);

    wx.showToast({
      title: '登录成功',
      icon: 'success'
    });

    setTimeout(() => {
      this.goToBookshelf();
    }, 800);
  },

  // 跳转到书架
  goToBookshelf() {
    wx.switchTab({
      url: '/pages/bookshelf/bookshelf'
    });
  }
});
