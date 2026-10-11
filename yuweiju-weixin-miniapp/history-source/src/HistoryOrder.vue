<template>
<view class="history_order">
<uni-nav-bar left-icon="back" leftIcon="arrowleft" title="历史订单" statusBar="true" fixed="true" color="#ffffff" backgroundColor="#333333" @clickLeft="goBack" />
<scroll-view class="scroll-row" scroll-x="true" :scroll-into-view="scrollinto" :scroll-with-animation="true">
<view v-for="(item, index) in tabBars" :key="index" class="scroll-row-item" :id="'tab'+index" @tap="changeTab(index)">
<view :class="tabIndex==index?'scroll-row-item-act':''"><text class="line"></text>{{item}}</view>
</view>
</scroll-view>
<swiper :style="'height:'+scrollH+'px;'" :current="tabIndex" @change="onChangeSwiperTab">
<swiper-item v-for="(tab, tabNumber) in tabBars" :key="tabNumber">
<scroll-view :style="'height:'+scrollH+'px;'" scroll-y="true" @scrolltolower="lower">
<view v-if="recentOrdersList && recentOrdersList.length>0" class="main recent_orders">
<view v-for="(item, index) in recentOrdersList" :key="index" :class="['box', 'order_lists', index+1===recentOrdersList.length?'item-last':'']">
<view class="date_type"><text class="time">{{item.orderTime}}</text><text :class="['type','status',item.status==2?'status':'']">{{statusWord(item.status,item.orderTime)}}</text></view>
<view class="orderBox" @tap="goDetail(item.id)">
<view class="food_num"><scroll-view class="pic" style="width:100%;overflow:hidden;white-space:nowrap;" scroll-x="true">
<view v-for="(num, y) in item.orderDetailList" :key="y" class="food_num_item"><view class="img"><image :src="num.image" /></view><view class="food">{{num.name}}</view></view>
</scroll-view></view>
<view class="numAndAum"><view><text>{{'￥'+item.amount.toFixed(2)}}</text></view><view><text>{{'共'+numes(item.orderDetailList).count+'件'}}</text></view></view>
</view>
<view class="againBtn"><button class="new_btn" type="default" @tap="oneMoreOrder(item.id)">再来一单</button>
<button v-if="item.status===1&&!getOvertime(item.orderTime)" class="new_btn btn" type="default" @tap="goDetail(item.id)">去支付</button>
<button v-if="item.status===2" class="new_btn btn" type="default" @tap="handleReminder('center',item.id)">催单</button></view>
</view>
</view>
<Empty v-if="isEmpty&&recentOrdersList.length===0" />
</scroll-view>
</swiper-item>
</swiper>
<uni-popup class="comPopupBox" ref="commonPopup"><view class="popup-content"><view class="text">{{textTip}}</view><view v-if="showConfirm" class="btn"><view @tap="closePopup">确认</view></view></view></uni-popup>
</view>
</template>
<script>
import page from "./history.js";
export default page;
</script>
<style>
@charset "UTF-8";
/**
 * 这里是uni-app内置的常用样式变量
 *
 * uni-app 官方扩展插件及插件市场（https://ext.dcloud.net.cn）上很多三方插件均使用了这些样式变量
 * 如果你是插件开发者，建议你使用scss预处理，并在插件代码中直接使用这些变量（无需 import 这个文件），方便用户通过搭积木的方式开发整体风格一致的App
 *
 */
/**
 * 如果你是App开发者（插件使用者），你可以通过修改这些变量来定制自己的插件主题，实现自定义主题功能
 *
 * 如果你的项目同样使用了scss预处理，你也可以直接在你的 scss 代码中使用如下变量，同时无需 import 这个文件
 */
/* 颜色变量 */
/* 行为相关颜色 */
/* 文字基本颜色 */
/* 背景颜色 */
/* 边框颜色 */
/* 尺寸变量 */
/* 文字尺寸 */
/* 图片尺寸 */
/* Border Radius */
/* 水平间距 */
/* 垂直间距 */
/* 透明度 */
/* 文章场景相关 */
.history_order.data-v-5cf07246 {
  height: 100%;
}
.history_order .recent_orders.data-v-5cf07246 {
  padding-top: 8rpx;
}
.scroll-row.data-v-5cf07246 {
  height: 88rpx;
  line-height: 88rpx;
  background-color: #fff;
  padding: 0 30rpx;
  display: flex;
  box-shadow: 0 2px 4px 0 rgba(0, 0, 0, 0.06);
}
.scroll-row-item.data-v-5cf07246 {
  margin-right: 88rpx;
  color: #666;
  display: inline-block;
  font-size: 28rpx;
}
.scroll-row-item-act.data-v-5cf07246 {
  color: #333;
  position: relative;
  font-weight: 600;
}
.scroll-row-item-act .line.data-v-5cf07246 {
  width: 32rpx;
  height: 8rpx;
  display: block;
  background: #6E1D20;
  border-radius: 8rpx;
  -webkit-transform: translate(-50%, -50%);
          transform: translate(-50%, -50%);
  position: absolute;
  bottom: -4rpx;
  left: 50%;
}


</style>
