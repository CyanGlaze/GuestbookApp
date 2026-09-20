const XLSX = require('../../utils/xlsx.mini.js');
const { getNavBarInfo } = require('../../utils/util.js');

Page({
  data: {
    books: [],
    userInfo: null,
    isRefreshing: false,
    showEditModal: false,
    editBookId: '',
    editTitle: '',
    editDesc: '',
    editIcon: '📋',
    iconList: ['📋', '📕', '📗', '📘', '📙', '📓', '📔', '📒', '📝', '💰', '🎁', '🎊'],
    viewMode: 'grid',
    isEmptyAfterDelete: false,
    showRenameModal: false,
    tempName: '',
    tempData: [],
    tempTotal: 0,
    statusBarHeight: 20,
    navBarHeight: 44
  },

  onLoad() {
    this.checkLogin();
    const nav = getNavBarInfo();
    this.setData({ statusBarHeight: nav.statusBarHeight, navBarHeight: nav.navBarHeight });
  },

  onShow() {
    this.loadBooks();
    this.loadUserInfo();
    const viewMode = wx.getStorageSync('shelfViewMode') || 'grid';
    this.setData({ viewMode });
  },

  checkLogin() {
    const userInfo = wx.getStorageSync('userInfo');
    if (!userInfo) {
      wx.reLaunch({ url: '/pages/login/login' });
      return false;
    }
    return true;
  },

  loadUserInfo() {
    const userInfo = wx.getStorageSync('userInfo');
    this.setData({ userInfo });
  },

  loadBooks() {
    try {
      const shelf = wx.getStorageSync('bookShelf') || [];
      this.setData({ books: shelf, isEmptyAfterDelete: false });
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
    const { id } = e.currentTarget.dataset;
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` });
  },

  onLongPressBook(e) {
    wx.vibrateShort({ type: 'medium' }).catch(() => {});
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
              editIcon: book?.icon || '📋'
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
          this.setData({ books: shelf, isEmptyAfterDelete: isEmpty });
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

  selectIcon(e) {
    const { icon } = e.currentTarget.dataset;
    this.setData({ editIcon: icon });
  },

  closeEditModal() {
    this.setData({ showEditModal: false });
  },

  confirmEdit() {
    const { editBookId, editTitle, editDesc, editIcon } = this.data;
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
        shelf[bookIndex].icon = editIcon;
        shelf[bookIndex].desc = (editDesc || '').trim();
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
      icon: '📋',
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
