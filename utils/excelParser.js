/**
 * Excel 解析工具（纯本地，无网络请求）。
 * 按表头名定位列，兼容「姓名/名字」「礼金/金额」等常见写法。
 */
const XLSX = require('./xlsx.mini.js');

function parseExcel(fileBuffer) {
  const result = [];
  try {
    const workbook = XLSX.read(fileBuffer, {
      type: 'array',
      cellStyles: false,
      cellText: true,
      sheetStubs: false,
      bookVBA: false
    });
    if (!workbook.SheetNames.length) return result;

    const worksheet = workbook.Sheets[workbook.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json(worksheet, { header: 1, defval: '', raw: false });
    if (rows.length < 2) return result;

    const headers = rows[0].map(h => String(h || '').trim());
    const idxOf = (names) => headers.findIndex(h => names.indexOf(h) > -1);
    const iName = idxOf(['姓名', '名字', '来宾']);
    const iAmount = idxOf(['礼金', '金额', '份子钱']);
    const iAddr = idxOf(['地址', '单位', '住址']);
    const iRemark = idxOf(['备注', '说明']);
    if (iName === -1) return result;

    for (let i = 1; i < rows.length; i++) {
      const row = rows[i] || [];
      const name = String(row[iName] || '').trim();
      if (!name) continue;
      const amount = iAmount > -1
        ? Number(String(row[iAmount] || '').replace(/[,，¥￥\s元]/g, '')) || 0
        : 0;
      result.push({
        '姓名': name,
        '礼金': amount,
        '地址': iAddr > -1 ? String(row[iAddr] || '').trim() : '',
        '备注': iRemark > -1 ? String(row[iRemark] || '').trim() : ''
      });
    }
  } catch (e) {
    console.error('Excel解析失败：', e);
    wx.showToast({ title: '文件解析失败，请确认是 .xlsx 格式', icon: 'none' });
  }
  return result;
}

module.exports = { parseExcel: parseExcel };
