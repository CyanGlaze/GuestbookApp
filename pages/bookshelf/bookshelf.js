const XLSX = require('../../utils/xlsx.mini.js');
const { getNavBarInfo } = require('../../utils/util.js');
const { withPrivacyAuth } = require('../../utils/privacy.js');

// 书本主体色板：默认参考图为喜庆红，其余供用户选择
const DEFAULT_COLORS = ['#c8323c', '#b8863b', '#2d5a43', '#3a5a9c', '#7a4b8c', '#b5546f'];

function toRgba(hex, alpha) {
  const h = String(hex || DEFAULT_COLORS[0]).replace('#', '');
  const r = parseInt(h.substring(0, 2), 16) || 0;
  const g = parseInt(h.substring(2, 4), 16) || 0;
  const b = parseInt(h.substring(4, 6), 16) || 0;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

Page({
  data: {
    books: [],
    userInfo: null,
    isLogin: false,
    isRefreshing: false,
    showEditModal: false,
    editBookId: '',
    editTitle: '',
    editDesc: '',
    colorOptions: DEFAULT_COLORS,
    editColor: DEFAULT_COLORS[0],
    viewMode: 'grid',
    isEmptyAfterDelete: false,
    locked: false,
    showRenameModal: false,
    tempName: '',
    tempData: [],
    tempTotal: 0,
    statusBarHeight: 20,
    navBarHeight: 44
  },

  onLoad() {
    // 导入/添加账本需先登录（在 mine 页完成），仅查看书架不强制；避免与隐私授权形成死循环
    const nav = getNavBarInfo();
    this.setData({ statusBarHeight: nav.statusBarHeight, navBarHeight: nav.navBarHeight });
  },

  onShow() {
    const isLogin = !!wx.getStorageSync('userInfo');
    this.setData({ isLogin, locked: !isLogin });
    // 退出登录后：不加载、不展示任何账本数据，仅提示登录
    if (!isLogin) {
      this.setData({ books: [], isEmptyAfterDelete: false, userInfo: null });
      return;
    }
    this.loadUserInfo();
    this.loadBooks();
    const viewMode = wx.getStorageSync('shelfViewMode') || 'grid';
    this.setData({ viewMode });
    this.autoOpenIfSingle();
  },

  onHide() {
    // 只有「自己进入详情页」才标记，避免返回书架后被再次自动带走
    if (this._enteringDetail) this._skipNextShow = true;
    this._enteringDetail = false;
  },

  // 统一进入详情页的入口
  openDetail(id) {
    if (id === undefined || id === null || id === '') return;
    this._enteringDetail = true;
    wx.navigateTo({ url: '/pages/detail/detail?id=' + id });
  },

  // 书架仅一本时直接打开该书；多本或空书架保持原展示流程
  autoOpenIfSingle() {
    if (!this.data.isLogin) return;
    if (this._skipNextShow) { this._skipNextShow = false; return; }
    let shelf;
    try { shelf = wx.getStorageSync('bookShelf') || []; } catch (e) { return; }
    if (!Array.isArray(shelf) || shelf.length !== 1) return;
    const pages = getCurrentPages();
    if (!pages.length || pages[pages.length - 1].route !== 'pages/bookshelf/bookshelf') return;
    const id = String(shelf[0].id);
    const go = () => this.openDetail(id);
    if (typeof wx.nextTick === 'function') wx.nextTick(go);
    else go();
  },

  loadUserInfo() {
    const userInfo = wx.getStorageSync('userInfo');
    this.setData({ userInfo, isLogin: !!userInfo });
  },

  // 给每本书补渲染用颜色：优先自定义 color，否则按序号取默认色板
  decorate(shelf) {
    return (shelf || []).map((b, i) => {
      const solid = b.color || DEFAULT_COLORS[i % DEFAULT_COLORS.length];
      return Object.assign({}, b, {
        displaySolid: solid,
        displayColor: toRgba(solid, 0.62)
      });
    });
  },

  loadBooks() {
    try {
      if (!this.data.isLogin) {
        this.setData({ books: [], isEmptyAfterDelete: false });
        return;
      }
      const shelf = wx.getStorageSync('bookShelf') || [];
      this.setData({ books: this.decorate(shelf), isEmptyAfterDelete: false });
    } catch (e) {
      console.error('读取书架数据失败', e);
      this.setData({ books: [], isEmptyAfterDelete: false });
    }
  },

  onRefresh() {
    this.setData({ isRefreshing: true });
    this.loadBooks();
    setTimeout(() => {
      this.setData({ isRefreshing: false });
    }, 500);
  },

  toggleView() {
    const newMode = this.data.viewMode === 'grid' ? 'list' : 'grid';
    this.setData({ viewMode: newMode });
    wx.setStorageSync('shelfViewMode', newMode);
  },

  openBook(e) {
    if (!this.data.isLogin) return;
    this.openDetail(e.currentTarget.dataset.id);
  },

  onLongPressBook(e) {
    if (!this.data.isLogin) return;
    // 兼容旧基础库：vibrateShort 不一定返回 Promise
    try {
      const p = wx.vibrateShort({ type: 'medium', fail: () => {} });
      if (p && typeof p.catch === 'function') p.catch(() => {});
    } catch (e) {}
    const { id, title } = e.currentTarget.dataset;
    const book = this.data.books.find(b => String(b.id) === String(id));
    wx.showActionSheet({
      itemList: ['✏️ 编辑账本', '📋 复制账本', '🗑️ 删除账本'],
      success: (res) => {
        switch (res.tapIndex) {
          case 0:
            this.setData({
              showEditModal: true,
              editBookId: String(id),
              editTitle: title,
              editDesc: book?.desc || '',
              editColor: book?.color || DEFAULT_COLORS[0]
            });
            break;
          case 1:
            this.copyBook(id);
            break;
          case 2:
            this.deleteBook(id, title);
            break;
        }
      }
    });
  },

  copyBook(id) {
    let shelf = wx.getStorageSync('bookShelf') || [];
    const bookIndex = shelf.findIndex(b => String(b.id) === String(id));
    if (bookIndex === -1) return;
    const originalBook = shelf[bookIndex];
    const now = new Date();
    const timeStr = `${(now.getMonth() + 1).toString().padStart(2, '0')}${now.getDate().toString().padStart(2, '0')}`;
    const newBook = {
      ...originalBook,
      id: Date.now().toString(),
      title: originalBook.title + `_副本${timeStr}`,
      date: now.toLocaleDateString()
    };
    shelf.splice(bookIndex + 1, 0, newBook);
    wx.setStorageSync('bookShelf', shelf);
    this.loadBooks();
    wx.showToast({ title: '复制成功', icon: 'success' });
  },

  deleteBook(id, title) {
    wx.showModal({
      title: '删除确认',
      content: `确定要删除《${title}》吗？删除后无法恢复。`,
      confirmColor: '#fa5151',
      success: (res) => {
        if (res.confirm) {
          let shelf = wx.getStorageSync('bookShelf') || [];
          shelf = shelf.filter(b => String(b.id) !== String(id));
          wx.setStorageSync('bookShelf', shelf);
          const isEmpty = shelf.length === 0;
          this.setData({ books: this.decorate(shelf), isEmptyAfterDelete: isEmpty });
          if (isEmpty) {
            wx.showToast({ title: '已删除全部账本', icon: 'none' });
          } else {
            wx.showToast({ title: '已删除', icon: 'success' });
          }
        }
      }
    });
  },

  onEditTitle(e) {
    this.setData({ editTitle: e.detail.value });
  },

  onEditDesc(e) {
    this.setData({ editDesc: e.detail.value });
  },

  // 选择书本颜色：沿用原「选择图标」的点击选中方式
  selectColor(e) {
    this.setData({ editColor: e.currentTarget.dataset.color });
  },

  closeEditModal() {
    this.setData({ showEditModal: false });
  },

  confirmEdit() {
    const { editBookId, editTitle, editDesc, editColor } = this.data;
    if (!editTitle.trim()) {
      wx.showToast({ title: '请输入账本名称', icon: 'none' });
      return;
    }
    try {
      let shelf = wx.getStorageSync('bookShelf') || [];
      // 统一转字符串匹配，防止数字/字符串类型不一致找不到
      const bookIndex = shelf.findIndex(b => String(b.id) === String(editBookId));
      
      if (bookIndex !== -1) {
        shelf[bookIndex].title = editTitle.trim();
        shelf[bookIndex].desc = (editDesc || '').trim();
        shelf[bookIndex].color = editColor;
        wx.setStorageSync('bookShelf', shelf);
        this.loadBooks();
        this.closeEditModal();
        wx.showToast({ title: '保存成功', icon: 'success' });
      } else {
        // 找不到时给提示，不再静默失败
        console.error('未找到要保存的账本，id:', editBookId);
        wx.showToast({ title: '保存失败，请重试', icon: 'none' });
        this.closeEditModal();
        this.loadBooks();
      }
    } catch (e) {
      console.error('保存异常', e);
      wx.showToast({ title: '保存出错', icon: 'none' });
    }
  },

  goToMine() {
    wx.switchTab({ url: '/pages/mine/mine' });
  },

  // ========== 导入账本（书架页直接导入，无需跳转 mine 页） ==========

  // 选择Excel文件（先经隐私授权）
  uploadExcel() {
    // 导入需先登录：未登录时引导去「我的」页完成登录，避免登录形同虚设
    if (!this.data.isLogin) {
      wx.showToast({ title: '请先在「我的」登录', icon: 'none' });
      setTimeout(() => wx.switchTab({ url: '/pages/mine/mine' }), 900);
      return;
    }
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

  // 确认保存导入的账本
  confirmImport() {
    if (!this.data.tempName.trim()) {
      wx.showToast({ title: '请输入账本名称', icon: 'none' });
      return;
    }

    const shelf = wx.getStorageSync('bookShelf') || [];
    const newBook = {
      id: Date.now().toString(),
      title: this.data.tempName.trim(),
      color: DEFAULT_COLORS[0],
      records: this.data.tempData,
      count: this.data.tempData.length,
      total: parseFloat(this.data.tempTotal),
      date: new Date().toLocaleDateString()
    };

    shelf.unshift(newBook);
    wx.setStorageSync('bookShelf', shelf);

    this.setData({ showRenameModal: false });
    this.loadBooks();
    wx.showToast({ title: '导入成功', icon: 'success' });
  },

  closeRenameModal() {
    this.setData({ showRenameModal: false });
  },

  doNothing() {},

  preventTouchMove() {}
});
