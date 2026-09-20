const PinyinMatch = require('../../utils/pinyin-match.js');
const { getNavBarInfo } = require('../../utils/util.js');

// 常见姓氏多音字：拼音字典含多个读音，按姓氏读音优先（模块级常量，页面实例可访问）
const SURNAME_MAP = {
  '单': 'S', '仇': 'Q', '沈': 'S', '曾': 'Z', '解': 'X', '查': 'Z', '乐': 'Y',
  '区': 'O', '朴': 'P', '尉': 'Y', '盖': 'G', '翟': 'Z', '牟': 'M', '员': 'Y',
  '覃': 'Q', '秘': 'B', '华': 'H', '燕': 'Y', '应': 'Y', '纪': 'J', '那': 'N',
  '折': 'S', '繁': 'P', '句': 'G', '相': 'X', '盛': 'S', '宁': 'N', '冼': 'X',
  '谌': 'C', '种': 'C', '藏': 'Z'
};

Page({
  data: {
    currentBookId: '',
    bookTitle: '',
    bookIcon: '📋',
    rawRecords: [],
    displayData: [],
    searchKey: '',
    searchCount: 0,
    totalCount: 0,
    totalAmount: 0,
    alphabet: "ABCDEFGHJKLMNOPQRSTWXYZ#".split(""),
    scrollTarget: '',
    currentLetter: '',
    showLetterTip: false,
    showEditModal: false,
    isAdding: false,
    editingItem: {},
    originalItem: {},
    statusBarHeight: 20,
    navBarHeight: 44,
    searchFocused: false
  },

  onLoad(options) {
    const nav = getNavBarInfo();
    this.setData({ statusBarHeight: nav.statusBarHeight, navBarHeight: nav.navBarHeight });
    if (options.id) {
      this.setData({ currentBookId: options.id });
      this.loadBookData(options.id);
    }
  },

  onShow() {
    if (this.data.currentBookId) {
      this.loadBookData(this.data.currentBookId);
    }
  },

  loadBookData(id) {
    const shelf = wx.getStorageSync('bookShelf') || [];
    const book = shelf.find(b => b.id === id);
    if (book) {
      const total = book.records.reduce((s, r) => s + (Number(r['礼金']) || 0), 0);
      this.setData({
        bookTitle: book.title,
        bookIcon: book.icon || '📋',
        rawRecords: book.records,
        totalCount: book.records.length,
        totalAmount: total.toFixed(2)
      });
      this.filterData(this.data.searchKey);
    }
  },

  // 返回上一页（自定义导航栏）
  goBack() {
    const pages = getCurrentPages();
    if (pages.length > 1) {
      wx.navigateBack();
    } else {
      wx.switchTab({ url: '/pages/bookshelf/bookshelf' });
    }
  },

  getAmountLevel(amount) {
    const a = Number(amount) || 0;
    if (a >= 1000) return 5;
    if (a >= 500) return 4;
    if (a >= 200) return 3;
    if (a >= 100) return 2;
    if (a >= 50) return 1;
    return 0;
  },

  getHighlight(text, key) {
    const fullText = String(text || '');
    if (!key) return [{ text: fullText, hit: false }];
    const k = key.toLowerCase().trim();
    const matchRes = PinyinMatch ? PinyinMatch.match(fullText, k) : false;
    if (matchRes) {
      const [start, end] = matchRes;
      const res = [];
      if (start > 0) res.push({ text: fullText.substring(0, start), hit: false });
      res.push({ text: fullText.substring(start, end + 1), hit: true });
      if (end < fullText.length - 1) res.push({ text: fullText.substring(end + 1), hit: false });
      return res;
    }
    const parts = k.split(/([^\u4e00-\u9fa5]+)/).filter(Boolean);
    let segments = [{ text: fullText, hit: false }];
    parts.forEach(part => {
      let newSegments = [];
      segments.forEach(seg => {
        if (seg.hit) { newSegments.push(seg); return; }
        const subLow = seg.text.toLowerCase();
        let hitIdx = -1, hitLen = 0;
        if (/[\u4e00-\u9fa5]/.test(part)) {
          hitIdx = subLow.indexOf(part);
          hitLen = part.length;
        } else if (PinyinMatch) {
          const m = PinyinMatch.match(seg.text, part);
          if (m) { hitIdx = m[0]; hitLen = m[1] - m[0] + 1; }
        }
        if (hitIdx > -1) {
          if (hitIdx > 0) newSegments.push({ text: seg.text.substring(0, hitIdx), hit: false });
          newSegments.push({ text: seg.text.substring(hitIdx, hitIdx + hitLen), hit: true });
          if (hitIdx + hitLen < seg.text.length) newSegments.push({ text: seg.text.substring(hitIdx + hitLen), hit: false });
        } else {
          newSegments.push(seg);
        }
      });
      segments = newSegments;
    });
    return segments;
  },

  smartMatch(target, key) {
    if (!key) return true;
    const t = String(target).toLowerCase(), k = String(key).toLowerCase().trim();
    if (t.includes(k) || (PinyinMatch && PinyinMatch.match(t, k))) return true;
    const parts = k.split(/([^\u4e00-\u9fa5]+)/).filter(Boolean);
    if (parts.length > 1) {
      let tempT = t;
      return parts.every(part => {
        if (/[\u4e00-\u9fa5]/.test(part)) {
          const idx = tempT.indexOf(part);
          if (idx === -1) return false;
          tempT = tempT.substring(idx + part.length);
          return true;
        } else {
          const res = PinyinMatch ? PinyinMatch.match(tempT, part) : false;
          if (res) { tempT = tempT.substring(res[1] + 1); return true; }
          return false;
        }
      });
    }
    return false;
  },

  filterData(key) {
    const allData = this.data.rawRecords || [];
    const filtered = allData.filter(item =>
      this.smartMatch(item['姓名'], key) || this.smartMatch(item['地址'], key)
    );
    const levelTags = { 2: '🎉', 3: '⭐', 4: '🔥', 5: '🧧' };
    const processed = filtered.map(item => {
      const level = this.getAmountLevel(item['礼金']);
      const letter = this.getFirstLetter(item['姓名']);
      return {
        ...item,
        nameSeg: this.getHighlight(item['姓名'], key),
        addrSeg: this.getHighlight(item['地址'], key),
        amountLevel: level,
        firstLetter: letter,
        avatarColor: letter.charCodeAt(0) % 6,
        levelTag: levelTags[level] || ''
      };
    });
    this.setData({ displayData: this.groupData(processed), searchCount: filtered.length });
  },

  groupData(flatData) {
    const groups = {};
    flatData.forEach(item => {
      const letter = this.getFirstLetter(item['姓名']);
      if (!groups[letter]) groups[letter] = [];
      groups[letter].push(item);
    });
    return Object.keys(groups).sort((a, b) => {
      if (a === '#') return 1;
      if (b === '#') return -1;
      return a.localeCompare(b);
    }).map(key => ({ letter: key, items: groups[key] }));
  },

  getFirstLetter(str) {
    if (!str) return '#';
    const firstChar = String(str).trim().charAt(0);
    if (/[a-zA-Z]/.test(firstChar)) return firstChar.toUpperCase();
    // 缓存：同一页面内相同的字只算一次
    if (!this._letterCache) this._letterCache = {};
    if (this._letterCache[firstChar]) return this._letterCache[firstChar];
    // 姓氏多音字优先按姓氏读音
    if (SURNAME_MAP[firstChar]) {
      this._letterCache[firstChar] = SURNAME_MAP[firstChar];
      return SURNAME_MAP[firstChar];
    }
    // 用拼音字典逐字母匹配（A-Z），跨端结果一致
    // 不再依赖 localeCompare：真机部分内核按 Unicode 码点排序，会把"陈"等字错分到 Z 组
    for (let i = 0; i < 26; i++) {
      const letter = String.fromCharCode(65 + i);
      if (PinyinMatch && PinyinMatch.match(firstChar, letter)) {
        this._letterCache[firstChar] = letter;
        return letter;
      }
    }
    this._letterCache[firstChar] = '#';
    return '#';
  },

  onSidebarTouchStart(e) {
    this.setData({ showLetterTip: true });
    this.computeLetter(e.touches[0].clientY);
  },
  onSidebarTouchMove(e) {
    this.computeLetter(e.touches[0].clientY);
  },
  onSidebarTouchEnd() {
    this.setData({ showLetterTip: false });
  },
  computeLetter(y) {
    const query = wx.createSelectorQuery();
    query.select('.sidebar').boundingClientRect(res => {
      if (res) {
        let idx = Math.floor((y - res.top) / (res.height / this.data.alphabet.length));
        idx = Math.max(0, Math.min(idx, this.data.alphabet.length - 1));
        const letter = this.data.alphabet[idx];
        if (letter !== this.data.currentLetter) {
          this.setData({
            currentLetter: letter,
            scrollTarget: 'letter-' + (letter === '#' ? 'special' : letter)
          });
          wx.vibrateShort({ type: 'light' });
        }
      }
    }).exec();
  },

  onSearch(e) {
    this.setData({ searchKey: e.detail.value });
    this.filterData(e.detail.value);
  },
  onSearchFocus() {
    this.setData({ searchFocused: true });
  },
  onSearchBlur() {
    this.setData({ searchFocused: false });
  },
  clearSearch() {
    this.setData({ searchKey: '' });
    this.filterData('');
  },

  onAddRecord() {
    this.setData({
      showEditModal: true,
      isAdding: true,
      editingItem: { '姓名': '', '礼金': '', '地址': '', '备注': '' }
    });
  },

  onLongPressCard(e) {
    const { item } = e.currentTarget.dataset;
    wx.showActionSheet({
      itemList: ['📝 编辑记录', '📋 复制记录', '🗑️ 删除记录'],
      success: (res) => {
        if (res.tapIndex === 0) {
          this.setData({
            showEditModal: true,
            isAdding: false,
            editingItem: { ...item },
            originalItem: { ...item }
          });
        } else if (res.tapIndex === 1) {
          this.copyRecord(item);
        } else if (res.tapIndex === 2) {
          this.deleteItem(item);
        }
      }
    });
  },

  // 只保留数据字段，避免把渲染辅助字段（nameSeg/addrSeg/amountLevel 等）存进账本
  pickRecordFields(item) {
    const clean = {};
    ['姓名', '礼金', '地址', '备注'].forEach(k => {
      if (item[k] !== undefined && item[k] !== null) clean[k] = item[k];
    });
    return clean;
  },

  copyRecord(item) {
    let shelf = wx.getStorageSync('bookShelf') || [];
    let bIdx = shelf.findIndex(b => b.id === this.data.currentBookId);
    if (bIdx === -1) return;
    const newRecord = this.pickRecordFields(item);
    shelf[bIdx].records.unshift(newRecord);
    shelf[bIdx].count = shelf[bIdx].records.length;
    shelf[bIdx].total = shelf[bIdx].records.reduce((s, r) => s + (Number(r['礼金']) || 0), 0);
    wx.setStorageSync('bookShelf', shelf);
    this.loadBookData(this.data.currentBookId);
    wx.showToast({ title: '已复制', icon: 'success' });
  },

  onEditInput(e) {
    this.setData({ [`editingItem.${e.currentTarget.dataset.key}`]: e.detail.value });
  },

  saveFullEdit() {
    const { editingItem, originalItem, isAdding, currentBookId } = this.data;
    if (!editingItem['姓名'] || !editingItem['礼金']) {
      wx.showToast({ title: '姓名和礼金必填', icon: 'none' });
      return;
    }
    let shelf = wx.getStorageSync('bookShelf') || [];
    let bIdx = shelf.findIndex(b => b.id === currentBookId);
    if (bIdx === -1) return;
    let records = shelf[bIdx].records;
    if (isAdding) {
      records.unshift(this.pickRecordFields(editingItem));
    } else {
      const rIdx = records.findIndex(d =>
        d['姓名'] === originalItem['姓名'] && d['礼金'] === originalItem['礼金']
      );
      if (rIdx > -1) records[rIdx] = this.pickRecordFields(editingItem);
    }
    shelf[bIdx].records = records;
    shelf[bIdx].count = records.length;
    shelf[bIdx].total = records.reduce((s, r) => s + (Number(r['礼金']) || 0), 0);
    wx.setStorageSync('bookShelf', shelf);
    this.setData({ showEditModal: false });
    this.loadBookData(currentBookId);
    wx.showToast({ title: '已保存', icon: 'success' });
  },

  closeEditModal() {
    this.setData({ showEditModal: false });
  },

  deleteItem(item) {
    wx.showModal({
      title: '确认删除',
      content: `确定删除 ${item['姓名']} 的记录吗？`,
      confirmColor: '#fa5151',
      success: (res) => {
        if (res.confirm) {
          let shelf = wx.getStorageSync('bookShelf') || [];
          let bIdx = shelf.findIndex(b => b.id === this.data.currentBookId);
          if (bIdx > -1) {
            let rs = shelf[bIdx].records;
            const rIdx = rs.findIndex(d =>
              d['姓名'] === item['姓名'] && d['礼金'] === item['礼金']
            );
            if (rIdx > -1) {
              rs.splice(rIdx, 1);
              shelf[bIdx].records = rs;
              shelf[bIdx].count = rs.length;
              shelf[bIdx].total = rs.reduce((s, r) => s + (Number(r['礼金']) || 0), 0);
              wx.setStorageSync('bookShelf', shelf);
              this.loadBookData(this.data.currentBookId);
              wx.showToast({ title: '已删除', icon: 'success' });
            }
          }
        }
      }
    });
  },

  preventTouchMove() {}
});
