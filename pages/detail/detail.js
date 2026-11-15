const PinyinMatch = require('../../utils/pinyin-match.js');
const { getNavBarInfo } = require('../../utils/util.js');

// 常见姓氏多音字：拼音字典含多个读音，按姓氏读音优先（模块级常量，页面实例可访问）
// 姓氏首字母映射（第一优先级）：多音字按姓氏读音 + 常用姓氏全覆盖，
// 避免依赖拼音字典逐字匹配时被字典错漏（如"彭"被误归 B）带偏。
const SURNAME_MAP = {
  // 多音字姓氏（按姓氏读音）
  '单': 'S', '仇': 'Q', '解': 'X', '查': 'Z', '乐': 'Y',
  '区': 'O', '朴': 'P', '尉': 'Y', '盖': 'G', '翟': 'Z', '牟': 'M', '员': 'Y',
  '覃': 'Q', '秘': 'B', '华': 'H', '燕': 'Y', '应': 'Y', '纪': 'J', '那': 'N',
  '折': 'S', '繁': 'P', '句': 'G', '相': 'X', '宁': 'N', '冼': 'X',
  '谌': 'S', '种': 'C', '藏': 'Z', '阚': 'K', '都': 'D',
  // 常用姓氏（人口前 150，字母序排列便于维护）
  '安': 'A', '白': 'B', '包': 'B', '鲍': 'B', '毕': 'B', '边': 'B', '卞': 'B', '伯': 'B',
  '蔡': 'C', '曹': 'C', '岑': 'C', '柴': 'C', '常': 'C', '晁': 'C', '陈': 'C', '成': 'C',
  '程': 'C', '池': 'C', '褚': 'C', '崔': 'C', '戴': 'D', '邓': 'D', '丁': 'D', '董': 'D',
  '杜': 'D', '段': 'D', '敦': 'D', '樊': 'F', '范': 'F', '方': 'F', '房': 'F', '费': 'F',
  '冯': 'F', '符': 'F', '傅': 'F', '甘': 'G', '高': 'G', '葛': 'G', '龚': 'G', '巩': 'G',
  '古': 'G', '顾': 'G', '关': 'G', '管': 'G', '桂': 'G', '郭': 'G', '韩': 'H', '杭': 'H',
  '郝': 'H', '何': 'H', '贺': 'H', '洪': 'H', '侯': 'H', '胡': 'H', '花': 'H', '黄': 'H',
  '霍': 'H', '姬': 'J', '吉': 'J', '纪': 'J', '季': 'J', '贾': 'J', '简': 'J', '江': 'J',
  '姜': 'J', '蒋': 'J', '金': 'J', '荆': 'J', '景': 'J', '康': 'C', '柯': 'K', '孔': 'K',
  '寇': 'K', '匡': 'K', '赖': 'L', '蓝': 'L', '郎': 'L', '雷': 'L', '黎': 'L', '李': 'L',
  '连': 'L', '廉': 'L', '梁': 'L', '廖': 'L', '林': 'L', '凌': 'L', '刘': 'L', '柳': 'L',
  '龙': 'L', '楼': 'L', '卢': 'L', '鲁': 'L', '陆': 'L', '路': 'L', '吕': 'L', '罗': 'R',
  '骆': 'L', '马': 'M', '麦': 'M', '梅': 'M', '孟': 'M', '闵': 'M', '莫': 'M', '牟': 'M',
  '穆': 'M', '倪': 'N', '聂': 'N', '欧': 'O', '欧阳': 'O', '潘': 'P', '庞': 'P', '彭': 'P',
  '皮': 'P', '蒲': 'P', '浦': 'P', '戚': 'Q', '祁': 'Q', '齐': 'Q', '钱': 'Q', '秦': 'Q',
  '邱': 'Q', '裘': 'Q', '屈': 'Q', '全': 'Q', '任': 'R', '阮': 'R', '芮': 'R', '商': 'S',
  '尚': 'S', '邵': 'S', '佘': 'S', '沈': 'S', '施': 'S', '石': 'S', '时': 'S', '史': 'S',
  '司徒': 'S', '苏': 'S', '孙': 'S', '隋': 'S', '汤': 'T', '唐': 'T', '陶': 'T', '田': 'T',
  '童': 'T', '涂': 'T', '万': 'W', '汪': 'W', '王': 'W', '韦': 'W', '卫': 'W', '魏': 'W',
  '温': 'W', '文': 'W', '翁': 'W', '吴': 'W', '伍': 'W', '武': 'W', '奚': 'X', '夏': 'X',
  '项': 'X', '萧': 'X', '肖': 'X', '谢': 'X', '邢': 'X', '徐': 'X', '许': 'X', '薛': 'X',
  '严': 'Y', '颜': 'Y', '杨': 'Y', '姚': 'Y', '叶': 'Y', '易': 'Y', '殷': 'Y', '尹': 'Y',
  '雍': 'Y', '尤': 'Y', '于': 'Y', '余': 'Y', '俞': 'Y', '虞': 'Y', '元': 'Y', '袁': 'Y',
  '岳': 'Y', '云': 'Y', '曾': 'Z', '詹': 'Z', '张': 'Z', '赵': 'Z', '郑': 'Z', '钟': 'Z',
  '仲': 'Z', '周': 'Z', '朱': 'Z', '祝': 'Z', '庄': 'Z', '卓': 'Z', '邹': 'Z', '祖': 'Z'
};

// 高亮切片带稳定 id，供 WXML 的 wx:key 使用
const withSegmentId = (segs) => segs.map((s, i) => ({ id: i, text: s.text, hit: s.hit }));

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
    const book = shelf.find(b => String(b.id) === String(id));
    if (book) {
      const records = book.records || [];
      const total = records.reduce((s, r) => s + (Number(r['礼金']) || 0), 0);
      this.setData({
        bookTitle: book.title,
        bookIcon: book.icon || '📋',
        rawRecords: records,
        totalCount: records.length,
        totalAmount: total.toFixed(2)
      });
      this.filterData(this.data.searchKey);
    }
  },

  // 返回上一页（自定义导航栏）；失败兜底回书架，不影响其他导航
  goBack() {
    const pages = getCurrentPages();
    if (pages.length > 1) {
      wx.navigateBack({
        delta: 1,
        fail: () => wx.switchTab({ url: '/pages/bookshelf/bookshelf' })
      });
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
    if (!key) return withSegmentId([{ text: fullText, hit: false }]);
    const k = key.toLowerCase().trim();
    const matchRes = PinyinMatch ? PinyinMatch.match(fullText, k) : false;
    if (matchRes) {
      const [start, end] = matchRes;
      const res = [];
      if (start > 0) res.push({ text: fullText.substring(0, start), hit: false });
      res.push({ text: fullText.substring(start, end + 1), hit: true });
      if (end < fullText.length - 1) res.push({ text: fullText.substring(end + 1), hit: false });
      return withSegmentId(res);
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
    return withSegmentId(segments);
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
    const processed = filtered.map((item, idx) => {
      const level = this.getAmountLevel(item['礼金']);
      const letter = this.getFirstLetter(item['姓名']);
      return {
        ...item,
        _key: 'r' + idx,
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
          try { wx.vibrateShort({ type: 'light', fail: () => {} }); } catch (e) {}
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
    let bIdx = shelf.findIndex(b => String(b.id) === String(this.data.currentBookId));
    if (bIdx === -1) return;
    const records = shelf[bIdx].records || [];
    const newRecord = this.pickRecordFields(item);
    records.unshift(newRecord);
    shelf[bIdx].records = records;
    shelf[bIdx].count = records.length;
    shelf[bIdx].total = records.reduce((s, r) => s + (Number(r['礼金']) || 0), 0);
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
    let bIdx = shelf.findIndex(b => String(b.id) === String(currentBookId));
    if (bIdx === -1) return;
    let records = shelf[bIdx].records || [];
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
          let bIdx = shelf.findIndex(b => String(b.id) === String(this.data.currentBookId));
          if (bIdx > -1) {
            let rs = shelf[bIdx].records || [];
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
