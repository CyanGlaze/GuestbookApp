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
    logging: false,
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
      // 千分位整数，避免 ¥48820.00 这类冗长数字
      totalAmount: String(Math.round(totalAmount)).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
    });
  },

  // ===== 页内登录（微信一键登录：并发获取头像 + 昵称，无昵称输入框）=====

  // 点击「微信一键登录」(open-type=chooseAvatar) → 系统选真实微信头像
  onChooseAvatar(e) {
    const { avatarUrl } = e.detail;
    if (!avatarUrl) {
      // 极少数机型返回空，走兜底登录，不卡死
      this.fallbackLogin();
      return;
    }
    // 并发执行两个独立任务（头像持久化 + 昵称获取），Promise.all 同时等待，非串行
    Promise.all([
      this.saveAvatar(avatarUrl),   // 任务1：持久化微信头像，返回稳定地址
      this.fetchNickname()          // 任务2：获取昵称（默认 / 可接后端）
    ]).then(([savedAvatar, nickName]) => {
      const finalAvatar = savedAvatar || avatarUrl; // 持久化失败则用临时路径兜底
      this.finishLogin({
        avatarType: 'image',
        avatarUrl: finalAvatar,
        nickName: nickName || '微信用户'
      });
    }).catch((err) => {
      console.error('登录信息获取失败:', err);
      wx.showToast({ title: '登录信息获取失败，已用默认资料', icon: 'none' });
      this.fallbackLogin(); // 任一任务异常也不卡死，用默认资料兜底
    });
  },

  // 任务1：把 chooseAvatar 的临时头像持久化到本地，返回稳定地址（Promise）
  saveAvatar(tempFilePath) {
    return new Promise((resolve) => {
      const fs = wx.getFileSystemManager();
      fs.saveFile({
        tempFilePath,
        success: (res) => resolve(res.savedFilePath),
        fail: () => resolve('') // 失败时返回空，由上层用临时路径兜底
      });
    });
  },

  // 任务2：获取昵称（Promise，便于与 saveAvatar 并发）。
  // 说明：微信现行隐私模型已无「程序化获取真实昵称」的接口（getUserProfile 已下线），
  // 故默认返回「微信用户」。若你提供后端昵称接口，把下方 resolve 改成 wx.request 即可，
  // 与 saveAvatar 经 Promise.all 并发，登录主流程无需改动。
  fetchNickname() {
    return new Promise((resolve) => {
      // 接后端示例（取消注释并替换 URL 即可）：
      // wx.request({ url: 'https://your-api/nickname',
      //   success: (r) => resolve((r.data && r.data.nickName) || '微信用户'),
      //   fail: () => resolve('微信用户') });
      resolve('微信用户');
    });
  },

  // 微信头像获取失败：多为后台《用户隐私保护指引》未声明「头像」或未生效。
  // 此处不阻塞登录，自动用默认头像完成登录，并提示去后台声明。
  onAvatarError(e) {
    console.error('chooseAvatar 失败:', e && e.detail);
    wx.showToast({ title: '微信头像暂不可用，已用默认头像', icon: 'none' });
    this.fallbackLogin();
  },

  // 头像获取失败 / 信息获取异常时的兜底：用默认头像 + 默认昵称完成登录，保证登录一定可用
  fallbackLogin() {
    this.finishLogin({
      avatarType: 'emoji',
      avatarEmoji: '🎉',
      avatarBg: 'linear-gradient(135deg,#5b9bff,#7c5bff)',
      nickName: '微信用户'
    });
  },

  // 协议说明：纯本地应用，数据不上传，仅说明用途
  showAgreement(e) {
    const type = e.currentTarget.dataset.type;
    if (type === 'privacy') {
      wx.showModal({
        title: '隐私政策',
        content: '本应用为纯本地记账工具：所有礼金数据仅保存在你的本机，不会上传任何服务器；仅在你主动选择文件时，在本机读取并解析 Excel / 备份内容用于生成或恢复礼金记录，不收集、不上传任何个人信息。',
        showCancel: false,
        confirmText: '知道了'
      });
    } else {
      wx.showModal({
        title: '用户协议',
        content: '你确认：本应用仅提供礼金账本的本地记录与管理，数据由你自行保管；清理微信缓存或卸载应用会导致数据丢失，建议定期使用「导出备份」保存。',
        showCancel: false,
        confirmText: '知道了'
      });
    }
  },

  // 完成登录：avatar 为 {avatarType:'image'|'emoji', nickName, ...}
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
          this.setData({ isLogin: false, userInfo: null });
        }
      }
    });
  },

  preventTouchMove() {}
});
