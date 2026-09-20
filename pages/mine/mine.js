const XLSX = require('../../utils/xlsx.mini.js'); 
const { getNavBarInfo } = require('../../utils/util.js');

Page({
  data: {
    isLogin: false,
    userInfo: null,
    showRenameModal: false,
    tempName: '',
    tempData: [],
    tempTotal: 0,
    totalBooks: 0,
    totalRecords: 0,
    totalAmount: '0.00',
    statusBarHeight: 20,
    navBarHeight: 44
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
      totalAmount: totalAmount.toFixed(2)
    });
  },

  // 登录
  onLogin() {
    wx.reLaunch({
      url: '/pages/login/login'
    });
  },

  // 选择Excel文件
  uploadExcel() {
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
            // 如果分享失败，尝试保存到本地
            wx.showModal({
              title: '导出成功',
              content: `文件已保存：${fileName}\n可从聊天记录中查看`,
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

  // 导入备份
  importBackup() {
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
  },

  // 清空所有数据
  clearAllData() {
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
    wx.showModal({
      title: '提示',
      content: '确定退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          wx.removeStorageSync('userInfo');
          this.setData({ isLogin: false, userInfo: null });
          wx.reLaunch({
            url: '/pages/login/login'
          });
        }
      }
    });
  },

  preventTouchMove() {}
});
