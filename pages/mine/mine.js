const XLSX = require('../../utils/xlsx.mini.js'); 
const { getNavBarInfo } = require('../../utils/util.js');
const { withPrivacyAuth } = require('../../utils/privacy.js');

// 触觉反馈：所有入口点击时轻震一下，确保真机上有明确的操作响应
function haptic() {
  try {
    wx.vibrateShort({ type: 'light', fail: () => {} });
  } catch (e) { /* 旧基础库无此 API 时忽略 */ }
}

Page({
  data: {
    isLogin: false,
    userInfo: null,
    tempAvatar: '',
    logging: false,
    showRenameModal: false,
    tempName: '',
    tempData: [],
    tempTotal: 0,
    totalBooks: 0,
    totalRecords: 0,
    totalAmount: '0.00',
    statusBarHeight: 20,
    navBarHeight: 44,
    // chooseAvatar 不可用时的兜底头像（表情 + 渐变底，本机直接渲染，不依赖隐私接口）
    presetAvatars: [
      { emoji: '😊', bg: 'linear-gradient(135deg,#5b9bff,#7c5bff)' },
      { emoji: '🌸', bg: 'linear-gradient(135deg,#ff8fb1,#ff5b8a)' },
      { emoji: '🍀', bg: 'linear-gradient(135deg,#43d17a,#1fae6b)' },
      { emoji: '⭐', bg: 'linear-gradient(135deg,#ffcf5b,#ff9f1c)' },
      { emoji: '🔥', bg: 'linear-gradient(135deg,#ff9a5b,#ff5b5b)' },
      { emoji: '💎', bg: 'linear-gradient(135deg,#9b7bff,#5b6fd8)' },
      { emoji: '🐱', bg: 'linear-gradient(135deg,#4fd1c5,#2bb6a8)' },
      { emoji: '🌟', bg: 'linear-gradient(135deg,#6f8bff,#4f5bd8)' }
    ],
    showAvatarPicker: false
  },

  onLoad() {
    const nav = getNavBarInfo();
    this.setData({ statusBarHeight: nav.statusBarHeight, navBarHeight: nav.navBarHeight });
    this.checkLogin();
  },

  onShow() {
    this.loadUserInfo();
    this.loadStats();
  },

  // 检查登录状态
  checkLogin() {
    const user = wx.getStorageSync('userInfo');
    if (user) {
      this.setData({ isLogin: true, userInfo: user });
    }
  },

  // 加载用户信息
  loadUserInfo() {
    const user = wx.getStorageSync('userInfo');
    if (user) {
      this.setData({ isLogin: true, userInfo: user });
    } else {
      this.setData({ isLogin: false, userInfo: null });
    }
  },

  // 加载统计数据
  loadStats() {
    const shelf = wx.getStorageSync('bookShelf') || [];
    let totalRecords = 0;
    let totalAmount = 0;
    
    shelf.forEach(book => {
      totalRecords += book.records?.length || 0;
      totalAmount += book.total || 0;
    });

    this.setData({
      totalBooks: shelf.length,
      totalRecords,
      // 千分位整数，避免 ¥48820.00 这类冗长数字
      totalAmount: String(Math.round(totalAmount)).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    });
  },

  // ===== 页内登录（替代独立登录页）=====

  // 弹窗内「使用微信头像」→ 系统选头像 → 选完自动完成登录
  onChooseAvatar(e) {
    const { avatarUrl } = e.detail;
    // 极少数机型 bindchooseavatar 返回的 detail 为空，提示改用预设头像
    if (!avatarUrl) {
      wx.showToast({ title: '未获取到头像，请选预设头像', icon: 'none' });
      return;
    }
    this.setData({ showAvatarPicker: false });
    this.finishLogin({ avatarType: 'image', avatarUrl });
  },

  // 微信头像获取失败：多为后台《用户隐私保护指引》未声明「头像」或未生效。
  // 此时头像弹窗已打开，仅提示用户改用预设头像即可，不卡死登录。
  onAvatarError(e) {
    console.error('chooseAvatar 失败:', e && e.detail);
    wx.showToast({ title: '微信头像暂不可用，请选预设头像', icon: 'none' });
  },

  // 打开备选头像选择（chooseAvatar 不可用时的兜底）
  openAvatarPicker() {
    if (this.data.logging) return;
    this.setData({ showAvatarPicker: true });
  },
  closeAvatarPicker() {
    this.setData({ showAvatarPicker: false });
  },
  // 从备选头像里选一个
  pickPresetAvatar(e) {
    const { emoji, bg } = e.currentTarget.dataset;
    this.setData({ showAvatarPicker: false });
    this.finishLogin({ avatarType: 'emoji', avatarEmoji: emoji, avatarBg: bg });
  },
  // 直接用默认头像登录
  useDefaultAvatar() {
    const def = this.data.presetAvatars[0];
    this.setData({ showAvatarPicker: false });
    this.finishLogin({ avatarType: 'emoji', avatarEmoji: def.emoji, avatarBg: def.bg });
  },

  // 完成登录：avatar 为 {avatarType:'image'|'emoji', ...}
  finishLogin(avatar) {
    if (this.data.logging) return;
    this.setData({ logging: true });

    const userInfo = Object.assign({
      nickName: '微信用户',
      loginTime: new Date().toISOString()
    }, avatar);

    wx.setStorageSync('userInfo', userInfo);

    wx.showToast({ title: '登录成功', icon: 'success' });

    setTimeout(() => {
      this.setData({ logging: false, isLogin: true, userInfo });
      this.loadStats();
    }, 600);
  },

  // 选择Excel文件（先经隐私授权）
  uploadExcel() {
    haptic();
    withPrivacyAuth(() => {
      wx.chooseMessageFile({
        count: 1,
        type: 'file',
        extension: ['.xlsx', '.xls'],
        success: (res) => {
          const file = res.tempFiles[0];
          const defaultName = file.name.replace(/\.[^/.]+$/, "");
          this.parseFile(file.path, defaultName);
        },
        fail: () => {
          wx.showToast({ title: '选择文件失败', icon: 'none' });
        }
      });
    });
  },

  // 解析文件
  parseFile(path, defaultName) {
    const fs = wx.getFileSystemManager();
    try {
      const buffer = fs.readFileSync(path);
      const workbook = XLSX.read(new Uint8Array(buffer), { type: 'array' });
      const worksheet = workbook.Sheets[workbook.SheetNames[0]];
      const jsonData = XLSX.utils.sheet_to_json(worksheet);

      if (jsonData.length === 0) {
        wx.showToast({ title: '文件内容为空', icon: 'none' });
        return;
      }

      // 计算总额
      const total = jsonData.reduce((s, r) => s + (Number(r['礼金']) || 0), 0);

      this.setData({
        tempName: defaultName,
        tempData: jsonData,
        tempTotal: total.toFixed(2),
        showRenameModal: true
      });
    } catch (e) {
      console.error('解析失败:', e);
      wx.showToast({ title: '文件解析失败', icon: 'none' });
    }
  },

  onInputName(e) {
    this.setData({ tempName: e.detail.value });
  },

  // 确认保存
  confirmSave() {
    if (!this.data.tempName.trim()) {
      wx.showToast({ title: '请输入账本名称', icon: 'none' });
      return;
    }

    const shelf = wx.getStorageSync('bookShelf') || [];
    const newBook = {
      id: Date.now().toString(),
      title: this.data.tempName.trim(),
      icon: '📋',
      records: this.data.tempData,
      count: this.data.tempData.length,
      total: parseFloat(this.data.tempTotal),
      date: new Date().toLocaleDateString()
    };

    shelf.unshift(newBook);
    wx.setStorageSync('bookShelf', shelf);

    this.setData({ showRenameModal: false });
    this.loadStats();
    wx.showToast({ title: '导入成功', icon: 'success' });
    
    // 跳转到书架
    setTimeout(() => {
      wx.switchTab({ url: '/pages/bookshelf/bookshelf' });
    }, 1000);
  },

  closeModal() {
    this.setData({ showRenameModal: false });
  },

  // 格式化时间戳
  formatTimestamp() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hour = String(now.getHours()).padStart(2, '0');
    const minute = String(now.getMinutes()).padStart(2, '0');
    const second = String(now.getSeconds()).padStart(2, '0');
    return `${year}${month}${day}_${hour}${minute}${second}`;
  },

  // 导出备份
  exportBackup() {
    haptic();
    const shelf = wx.getStorageSync('bookShelf') || [];
    if (shelf.length === 0) {
      wx.showToast({ title: '暂无数据可导出', icon: 'none' });
      return;
    }

    const dataStr = JSON.stringify(shelf, null, 2);
    const fs = wx.getFileSystemManager();
    const timestamp = this.formatTimestamp();
    const fileName = `礼金账本备份_${timestamp}.json`;
    const filePath = `${wx.env.USER_DATA_PATH}/${fileName}`;

    fs.writeFile({
      filePath,
      data: dataStr,
      encoding: 'utf8',
      success: () => {
        wx.shareFileMessage({
          filePath,
          fileName: fileName,
          success: () => {
            wx.showToast({ title: '导出成功', icon: 'success' });
          },
        fail: (err) => {
          console.error('分享失败:', err);
          // 开发者工具不支持 shareFileMessage（真机专属 API），真机上正常
          const isDevtools = wx.getSystemInfoSync().platform === 'devtools';
          wx.showModal({
            title: isDevtools ? '请在真机上导出' : '导出完成',
            content: isDevtools
              ? '开发者工具不支持唤起微信分享，请用手机预览或体验版在本机导出备份。'
              : `备份文件已生成：${fileName}`,
            showCancel: false
          });
        }
        });
      },
      fail: (err) => {
        console.error('导出失败:', err);
        wx.showToast({ title: '导出失败', icon: 'none' });
      }
    });
  },

  // 导入备份（先经隐私授权）
  importBackup() {
    haptic();
    withPrivacyAuth(() => {
      wx.chooseMessageFile({
        count: 1,
        type: 'file',
        extension: ['.json'],
      success: (res) => {
        const fs = wx.getFileSystemManager();
        try {
          const data = fs.readFileSync(res.tempFiles[0].path, 'utf8');
          const backupData = JSON.parse(data);
          
          if (!Array.isArray(backupData)) {
            wx.showToast({ title: '备份文件格式错误', icon: 'none' });
            return;
          }

          wx.showModal({
            title: '导入确认',
            content: `检测到 ${backupData.length} 本账本，是否覆盖当前数据？`,
            success: (modalRes) => {
              if (modalRes.confirm) {
                wx.setStorageSync('bookShelf', backupData);
                this.loadStats();
                wx.showToast({ title: '导入成功', icon: 'success' });
              }
            }
          });
        } catch (e) {
          wx.showToast({ title: '文件解析失败', icon: 'none' });
        }
      }
      });
    });
  },

  // 清空所有数据
  clearAllData() {
    haptic();
    wx.showModal({
      title: '危险操作',
      content: '确定要清空所有数据吗？此操作不可恢复！',
      confirmColor: '#fa5151',
      success: (res) => {
        if (res.confirm) {
          wx.removeStorageSync('bookShelf');
          this.loadStats();
          wx.showToast({ title: '已清空', icon: 'success' });
        }
      }
    });
  },

  // 退出登录
  onLogout() {
    haptic();
    wx.showModal({
      title: '提示',
      content: '确定退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          wx.removeStorageSync('userInfo');
          // 回到未登录态：「我的」页重新显示居中登录卡
          this.setData({ isLogin: false, userInfo: null, tempAvatar: '' });
        }
      }
    });
  },

  preventTouchMove() {}
});
