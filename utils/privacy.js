// 隐私接口授权封装
// 微信要求：chooseMessageFile 等受管控隐私接口，调用前必须先取得用户隐私授权。
// requirePrivacyAuthorize 需由用户手势触发（点击），chooseMessageFile 本身已在 tap 回调内，故可直接包裹。
// 兼容旧基础库（无该 API 时直接执行回调）。
function withPrivacyAuth(callback) {
  if (typeof wx.requirePrivacyAuthorize === 'function') {
    wx.requirePrivacyAuthorize({
      success: callback,
      fail: function (res) {
        var msg = (res && res.errMsg) || '';
        if (msg.indexOf('cancel') > -1) {
          // 用户主动取消授权
          wx.showToast({ title: '已取消', icon: 'none' });
        } else if (msg.indexOf('privacy') > -1 || msg.indexOf('scope') > -1) {
          // 隐私指引未声明或未生效：平台拦截，明确告知解决办法
          wx.showModal({
            title: '导入功能暂不可用',
            content: '需在小程序后台《用户隐私保护指引》中声明「选中的文件」并审核通过后，才能选择文件导入。',
            showCancel: false,
            confirmText: '知道了'
          });
        } else {
          wx.showToast({ title: '操作未完成', icon: 'none' });
        }
      }
    });
  } else {
    callback();
  }
}

module.exports = { withPrivacyAuth };
