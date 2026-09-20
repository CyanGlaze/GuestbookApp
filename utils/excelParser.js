// 本地引入xlsx，不依赖npm
const XLSX = require('./xlsx.js');

/**
 * 解析Excel为标准object[]
 * @param {ArrayBuffer} fileBuffer Excel二进制数据
 * @returns {Array} 格式化后的礼金数据
 */
export function parseExcel(fileBuffer) {
  let result = []; // 强制初始化为空数组
  try {
    // 纯前端解析配置，无Node.js依赖
    const workbook = XLSX.read(fileBuffer, {
      type: 'array',
      cellStyles: false,
      cellText: true,
      sheetStubs: false,
      bookVBA: false
    });

    // 无工作表则返回空数组
    if (workbook.SheetNames.length === 0) {
      wx.showToast({ title: 'Excel无有效工作表', icon: 'none' });
      return result;
    }

    // 读取第一个工作表
    const sheetName = workbook.SheetNames[0];
    const worksheet = workbook.Sheets[sheetName];

    // 转为JSON并兜底
    const jsonData = XLSX.utils.sheet_to_json(worksheet, {
      header: ['地址', '姓名', '礼金', '备注'],
      skipHeader: true
    }) || [];

    // 过滤空数据，格式化字段
    result = jsonData.map(item => {
      const safeItem = typeof item === 'object' && item !== null ? item : {};
      return {
        姓名: safeItem.姓名 || '无',
        地址: safeItem.地址 || '无',
        礼金: safeItem.礼金 !== undefined ? safeItem.礼金 : '无',
        备注: safeItem.备注 || '无'
      };
    }).filter(item => item.姓名 !== '无' || item.地址 !== '无');

  } catch (e) {
    console.error('Excel解析失败：', e);
    wx.showToast({ title: '仅支持.xlsx格式', icon: 'none' });
  }
  return result;
}