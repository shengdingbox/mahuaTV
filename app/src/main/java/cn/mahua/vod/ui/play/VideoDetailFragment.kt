package cn.mahua.vod.ui.play

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.mahua.vod.ApiConfig
import cn.mahua.vod.App
import cn.mahua.vod.R
import cn.mahua.vod.ad.AdWebView
import cn.mahua.vod.bean.*
import cn.mahua.vod.netservice.VodService
import cn.mahua.vod.ui.feedback.FeedbackActivity
import cn.mahua.vod.ui.home.MyDividerItemDecoration
import cn.mahua.vod.ui.login.LoginActivity
import cn.mahua.vod.ui.share.ShareActivity
import cn.mahua.vod.utils.AgainstCheatUtil
import cn.mahua.vod.utils.DensityUtils.dp2px
import cn.mahua.vod.utils.DensityUtils.getScreenWidth
import cn.mahua.vod.utils.Retrofit2Utils
import cn.mahua.vod.utils.UserUtils
import com.blankj.utilcode.util.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.StormWyrm.wanandroid.base.exception.ResponseException
import com.github.StormWyrm.wanandroid.base.fragment.BaseFragment
import com.github.StormWyrm.wanandroid.base.net.RequestManager
import com.github.StormWyrm.wanandroid.base.net.observer.BaseObserver
import com.google.android.material.tabs.TabLayout
import com.scwang.smartrefresh.layout.footer.ClassicsFooter
import kotlinx.android.synthetic.main.fragment_play_detail.*
import org.litepal.LitePalApplication

class VideoDetailFragment : BaseFragment() {

    private lateinit var mVodBean: VodBean
    private var isCollected: Boolean = false
    private var urlIndex: Int = 0 //播放集
    private var playSourceIndex: Int = 0//播放源
    private var curCommentPage = 1
    private lateinit var vod_play_list: List<PlayFromBean>//播放视频列表
    private var curType = 0//推荐 默认是相似推荐
    private var curSameTypePage = 1
    private var curSameActorPage = 1
    private val recommendAdapter: RecommendAdapter by lazy {
        RecommendAdapter().apply {
            setOnItemClickListener { adapter, view, position ->
                val vodBean = adapter.getItem(position) as VodBean
                playActivity.showNewVideo(vodBean)
            }
        }
    }
    private val commentAdapter: CommentAdapter by lazy {
        CommentAdapter().apply {
            setHeaderAndEmpty(true)
        }
    }
    private val selectionAdapter: SelectionAdapter by lazy {
        SelectionAdapter().apply {
            setOnItemClickListener { adapter, view, position ->
                if (urlIndex != position) {
                    urlIndex = position
                    playActivity.changeSelection(position)
                    notifyDataSetChanged()
                }
            }
        }
    }

    private val headerView: View by lazy {
        View.inflate(mActivity, R.layout.layout_video_detail, null)
    }
    private lateinit var rvLastest: RecyclerView
    private lateinit var tlPlaySource: TabLayout
    private lateinit var playActivity: NewPlayActivity

    override fun getLayoutId(): Int {
        return R.layout.fragment_play_detail
    }

    override fun initView() {
        super.initView()
        playActivity = mActivity as NewPlayActivity
        arguments?.run {
            mVodBean = getParcelable(VOD_BEAN) ?: null as VodBean
            urlIndex = getInt(URL_INDEX)
            playSourceIndex = getInt(PLAY_SOURCE_INDEX)
        }

        refreshLayout.setEnableRefresh(false)
        refreshLayout.setRefreshFooter(ClassicsFooter(mActivity))

        rvPlayDetail.layoutManager = LinearLayoutManager(mActivity)
        rvPlayDetail.adapter = commentAdapter
        initHeaderMsg()
        commentAdapter.addHeaderView(headerView)

        getCommentList()
        getSameTypeData()
    }

    override fun onResume() {
        super.onResume()
        getCollectionState()
    }

    fun changeCurIndex(urlIndex: Int) {
        this.urlIndex = urlIndex
        selectionAdapter.notifyDataSetChanged()
        scrollCurIndex(rvLastest)
    }

    @SuppressLint("SetTextI18n")
    private fun initHeaderMsg() {
        val title = headerView.findViewById<TextView>(R.id.item_tv_playinfo_title)
        val intro = headerView.findViewById<TextView>(R.id.item_tv_playinfo_intro)
        val hits = headerView.findViewById<TextView>(R.id.item_tv_playinfo_hits)
        val score = headerView.findViewById<TextView>(R.id.item_tv_playinfo_score)
        val tvLastest = headerView.findViewById<TextView>(R.id.tvLastest)
        val ivLastest = headerView.findViewById<ImageView>(R.id.iv_lastest)
        val sortVodView = headerView.findViewById<TextView>(R.id.item_svv_playinfo)
        tlPlaySource = headerView.findViewById<TabLayout>(R.id.tlPlaySource)
        rvLastest = headerView.findViewById<RecyclerView>(R.id.rvLastest)
        val item_tv_playinfo_comment = headerView.findViewById<TextView>(R.id.item_tv_playinfo_comment)
        val awvPlayerDown = headerView.findViewById<AdWebView>(R.id.awvPlayerDown)
        val ad = App.startBean?.ads?.player_down
        if (ad == null || ad.status == 0 || ad.description.isNullOrEmpty()) {
            awvPlayerDown.visibility = View.GONE
        } else {
            awvPlayerDown.visibility = View.VISIBLE
            awvPlayerDown.loadHtmlBody(ad.description)
        }
        headerView.findViewById<TextView>(R.id.item_tv_playinfo_grade).setOnClickListener {
            score()
        }
        headerView.findViewById<TextView>(R.id.item_tv_playinfo_collect)
                .setOnClickListener {
                    if (UserUtils.isLogin()) {
                        if (isCollected) {
                            uncollection()
                        } else {
                            collection()
                        }
                    } else {
                        ActivityUtils.startActivity(LoginActivity::class.java)
                    }

                }
        headerView.findViewById<TextView>(R.id.item_tv_playinfo_download)
                .setOnClickListener {
                    ToastUtils.showShort("功能开发中，敬请期待...")
                }
        headerView.findViewById<TextView>(R.id.item_tv_playinfo_feedback)
                .setOnClickListener {
                    ActivityUtils.startActivity(FeedbackActivity::class.java)
                }
        headerView.findViewById<TextView>(R.id.item_tv_playinfo_share)
                .setOnClickListener {
                    ShareActivity.start()
                }
        tvLastest.setOnClickListener {
            playActivity.showPlayList()
        }

        item_tv_playinfo_comment.text = "${mVodBean.comment_num} 评论"
        title.text = mVodBean.vod_name
        hits.text = "播放 " + mVodBean.vod_hits + "次"
        score.text = mVodBean.vod_score + "分"
        sortVodView.text = mVodBean.vod_class.replace(",", " ")
        intro.setOnClickListener {
            playActivity.showSummary()
        }

        if (mVodBean.vodRemarks.isNotEmpty()) {
            tvLastest.text = mVodBean.vodRemarks//选集
            ivLastest.visibility = View.VISIBLE
        } else {
            ivLastest.visibility = View.GONE
        }

        vod_play_list = mVodBean.vod_play_list
        rvLastest.layoutManager = LinearLayoutManager(mActivity).apply {
            orientation = LinearLayoutManager.HORIZONTAL
        }
        rvLastest.adapter = selectionAdapter

        if (vod_play_list != null && vod_play_list!!.size != 0) {
            for (i in vod_play_list!!.indices) {
                val playFromBean = vod_play_list!!.get(i)
                val player_info = playFromBean.player_info
                val urls = playFromBean.urls
                var playSource = player_info.show
                if (StringUtils.isEmpty(playSource)) {
                    playSource = "默认"
                }
                if (i == playSourceIndex) {
                    selectionAdapter.addData(urls)
                }

                val tab = tlPlaySource.newTab().setText(playSource)
                tlPlaySource.addTab(tab)
            }
        }

        tlPlaySource.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(p0: TabLayout.Tab?) {
                Log.d("", "")
            }

            override fun onTabUnselected(p0: TabLayout.Tab?) {
                Log.d("", "")
            }

            override fun onTabSelected(p0: TabLayout.Tab?) {
                val playFromBean = vod_play_list[tlPlaySource.selectedTabPosition]
                selectionAdapter.setNewData(playFromBean.urls)
                playActivity.changePlaySource(playFromBean, tlPlaySource.selectedTabPosition)
            }

        })

        tlPlaySource.getTabAt(playSourceIndex)?.select()
        scrollCurIndex(rvLastest)


        val rvRecommand = headerView.findViewById<RecyclerView>(R.id.rvRecommand)
        val tvChange = headerView.findViewById<TextView>(R.id.tvChange)
        val tvSameType = headerView.findViewById<TextView>(R.id.tvSameType)
        val tvSameActor = headerView.findViewById<TextView>(R.id.tvSameActor)
        val dividerItemDecoration = MyDividerItemDecoration(mActivity, RecyclerView.HORIZONTAL, false)
        dividerItemDecoration.setDrawable(mActivity.resources.getDrawable(R.drawable.divider_image))
        rvRecommand.addItemDecoration(dividerItemDecoration)
        rvRecommand.layoutManager = GridLayoutManager(mActivity, 3, RecyclerView.VERTICAL, false).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return 3
                }
            }
        }
        rvRecommand.adapter = recommendAdapter


        tvChange.setOnClickListener {
            when (curType) {
                0 -> getSameTypeData()
                1 -> getSameActorData()
            }
        }
        tvSameType.setOnClickListener {
            if (curType != 0) {
                curType = 0
                tvSameType.setTextColor(ColorUtils.getColor(R.color.colorPrimary))
                tvSameActor.setTextColor(ColorUtils.getColor(R.color.gray_999))
                getSameTypeData()
            }
        }

        tvSameActor.setOnClickListener {
            if (curType != 1) {
                curType = 1
                tvSameType.setTextColor(ColorUtils.getColor(R.color.gray_999))
                tvSameActor.setTextColor(ColorUtils.getColor(R.color.colorPrimary))
                getSameActorData()
            }
        }
        rlComment.setOnClickListener {
            if (UserUtils.isLogin()) {
                CommentDialog(mActivity)
                        .setOnCommentSubmitClickListener(object : CommentDialog.OnCommentSubmitClickListener {
                            override fun onCommentSubmit(comment: String) {
                                commitComment(comment)
                            }
                        })
                        .show()
            } else {
                LoginActivity.start()
            }

        }
    }

    private fun scrollCurIndex(rvLastest: RecyclerView) {
        rvLastest.scrollToPosition(urlIndex)
        val mLayoutManager = rvLastest.layoutManager as LinearLayoutManager
        mLayoutManager.scrollToPositionWithOffset(urlIndex, 0)
    }

    override fun initListener() {
        super.initListener()
        refreshLayout.setOnLoadMoreListener {
            curCommentPage++
            getCommentList()
        }
    }

    private fun collection() {
        val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
        if (AgainstCheatUtil.showWarn(vodService)) {
            return
        }
        RequestManager.execute(this,
                vodService.collect(1.toString(), mVodBean.vod_id.toString(), 2.toString()),
                object : BaseObserver<String>() {
                    override fun onSuccess(data: String) {
                        ToastUtils.showShort("已收藏")
                        val drawable = mActivity.getDrawable(R.drawable.ic_collected)
                        isCollected = true
                        drawable?.setBounds(0, 0, drawable.minimumWidth,
                                drawable.minimumHeight)
                        headerView.findViewById<TextView>(R.id.item_tv_playinfo_collect)
                                .apply {
                                    setCompoundDrawables(null, drawable, null, null)
                                    text = "已收藏"
                                }
                    }

                    override fun onError(e: ResponseException) {

                    }

                })
    }

    private fun uncollection() {
        val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
        if (AgainstCheatUtil.showWarn(vodService)) {
            return
        }
        RequestManager.execute(this,
                vodService.deleteCollect(mVodBean.vod_id.toString(), 2.toString()),
                object : BaseObserver<String>() {
                    override fun onSuccess(data: String) {
                        ToastUtils.showShort("取消成功")
                        isCollected = false
                        val drawable = mActivity.getDrawable(R.drawable.ic_collection)
                        drawable?.setBounds(0, 0, drawable.minimumWidth,
                                drawable.minimumHeight)
                        headerView.findViewById<TextView>(R.id.item_tv_playinfo_collect)
                                .apply {
                                    setCompoundDrawables(null, drawable, null, null)
                                    text = "收藏"
                                }
                    }

                    override fun onError(e: ResponseException) {
                    }

                }
        )
    }

    private fun getCollectionState() {
        if (UserUtils.isLogin()) {
            val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
            if (AgainstCheatUtil.showWarn(vodService)) {
                return
            }
            RequestManager.execute(this,
                    vodService.getCollectList(1.toString(), 100.toString(), 2.toString()),
                    object : BaseObserver<Page<CollectionBean>>() {
                        override fun onSuccess(data: Page<CollectionBean>) {
                            for (bean in data.list) {
                                if (bean.data.id == mVodBean.vod_id) {
                                    isCollected = true
                                    break
                                }
                            }
                            if (isCollected) {
                                val drawable = mActivity.getDrawable(R.drawable.ic_collected)
                                drawable?.setBounds(0, 0, drawable.minimumWidth,
                                        drawable.minimumHeight)
                                headerView.findViewById<TextView>(R.id.item_tv_playinfo_collect)
                                        .apply {
                                            setCompoundDrawables(null, drawable, null, null)
                                            text = "已收藏"
                                        }
                            } else {
                                val drawable = mActivity.getDrawable(R.drawable.ic_collection)
                                drawable?.setBounds(0, 0, drawable.minimumWidth,
                                        drawable.minimumHeight)
                                headerView.findViewById<TextView>(R.id.item_tv_playinfo_collect)
                                        .apply {
                                            setCompoundDrawables(null, drawable, null, null)
                                            text = "收藏"
                                        }
                            }

                        }

                        override fun onError(e: ResponseException) {

                        }

                    })
        }
    }

    private fun score() {
        val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
        if (AgainstCheatUtil.showWarn(vodService)) {
            return
        }
        ScoreDialog(mActivity)
                .setOnScoreSubmitClickListener(object : ScoreDialog.OnScoreSubmitClickListener {
                    override fun onScoreSubmit(scoreDialog: ScoreDialog, score: Float) {
                        if (score == 0f) {
                            ToastUtils.showShort("评分不能为空!")
                        } else {
                            scoreDialog.dismiss()
                            val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
                            if (AgainstCheatUtil.showWarn(vodService)) {
                                return
                            }
                            RequestManager.execute(
                                    this@VideoDetailFragment,
                                    vodService.score(mVodBean.vod_id.toString(), score.toString()),
                                    object : BaseObserver<GetScoreBean>() {
                                        override fun onSuccess(data: GetScoreBean) {
                                            if (data.score != "0") {
                                                ToastUtils.showShort("评分成功，获得${data.score}积分")
                                            }
                                        }

                                        override fun onError(e: ResponseException) {
                                        }
                                    }

                            )
                        }
                    }
                })
                .show()
    }

    private fun commitComment(commentContent: String) {
        val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
        if (AgainstCheatUtil.showWarn(vodService)) {
            return
        }
        RequestManager.execute(this,
                vodService.comment(commentContent, 1.toString(), mVodBean.vod_id.toString()),
                object : BaseObserver<GetScoreBean>() {
                    override fun onSuccess(data: GetScoreBean) {
                        if (data.score == "0") {
                            ToastUtils.showShort("评论成功")
                        } else {
                            ToastUtils.showShort("评论成功,获得${data.score}积分")
                        }
                        curCommentPage = 1
                        getCommentList(true)
                    }

                    override fun onError(e: ResponseException) {

                    }

                })
    }

    private fun replayComment(commentContent: String, commentId: String, commentPid: String) {
        val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
        if (AgainstCheatUtil.showWarn(vodService)) {
            return
        }
        RequestManager.execute(this,
                vodService.replayComment(commentContent, 1.toString(), mVodBean.vod_id.toString(), commentId, commentPid),
                object : BaseObserver<String>() {
                    override fun onSuccess(data: String) {

                    }

                    override fun onError(e: ResponseException) {

                    }

                })
    }

    private fun getCommentList(isFresh: Boolean = false) {
        val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
        if (AgainstCheatUtil.showWarn(vodService)) {
            return
        }
        RequestManager.execute(this,
                vodService.getCommentList(mVodBean.vod_id, 1.toString(), curCommentPage, 10),
                object : BaseObserver<Page<CommentBean>>() {
                    override fun onSuccess(data: Page<CommentBean>) {
                        if (curCommentPage == 1) {
                            if (isFresh)
                                commentAdapter.setNewData(data.list)
                            else
                                commentAdapter.addData(data.list)
                        }

                        if (curCommentPage > 1) {
                            commentAdapter.addData(data.list)
                            if (refreshLayout != null) {
                                if (data.list.isEmpty()) {
                                    refreshLayout.finishLoadMoreWithNoMoreData()
                                } else {
                                    refreshLayout.finishLoadMore(true)
                                }
                            }
                        }
                    }

                    override fun onError(e: ResponseException) {
                        if (curCommentPage > 1 && refreshLayout != null) {
                            refreshLayout.finishLoadMore(false)
                        }
                    }

                })
    }

    private fun getSameTypeData() {
        val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
        if (AgainstCheatUtil.showWarn(vodService)) {
            return
        }
        RequestManager.execute(this,
                vodService.getSameTypeList(mVodBean.type_id, mVodBean.vod_class, curSameTypePage, 3),
                object : BaseObserver<Page<VodBean>>() {
                    override fun onSuccess(data: Page<VodBean>) {
                        if (data.list.isNotEmpty()) {
                            curSameTypePage++
                            recommendAdapter.setNewData(data.list)
                        }
                    }

                    override fun onError(e: ResponseException) {
                    }

                })
    }

    private fun getSameActorData() {
        val vodService = Retrofit2Utils.INSTANCE.createByGson(VodService::class.java)
        if (AgainstCheatUtil.showWarn(vodService)) {
            return
        }
        RequestManager.execute(this,
                vodService.getSameActorList(mVodBean.type_id, mVodBean.vod_actor, curSameActorPage, 3),
                object : BaseObserver<Page<VodBean>>() {
                    override fun onSuccess(data: Page<VodBean>) {
                        if (data.list.isNotEmpty()) {
                            recommendAdapter.setNewData(data.list)
                            curSameActorPage++
                        }
                    }

                    override fun onError(e: ResponseException) {

                    }

                })
    }

    fun changePlaysource(playSourceIndex: Int) {
        tlPlaySource.getTabAt(playSourceIndex)?.select()
    }

    private class CommentAdapter : BaseQuickAdapter<CommentBean, BaseViewHolder>(R.layout.item_hot_comment) {
        override fun convert(helper: BaseViewHolder, item: CommentBean?) {
            helper.let {
                item?.run {
                    it.setText(R.id.tvUser, comment_name)
                    it.setText(R.id.tvTime, TimeUtils.millis2String(comment_time * 1000))
                    it.setText(R.id.tvComment, comment_content)
                    val ivAvator = it.getView<ImageView>(R.id.ivAvatar)
                    if (user_portrait.isNotEmpty()) {
                        Glide.with(helper.convertView)
                                .load(ApiConfig.BASE_URL + "/" + user_portrait)
                                .apply(RequestOptions.bitmapTransform(CircleCrop()))
                                .into(ivAvator)
                    } else {
                        Glide.with(helper.convertView)
                                .load(R.drawable.ic_default_avator)
                                .apply(RequestOptions.bitmapTransform(CircleCrop()))
                                .into(ivAvator)
                    }
                }
            }
        }


    }

    private class RecommendAdapter : BaseQuickAdapter<VodBean, BaseViewHolder>(R.layout.item_card_child) {

        override fun convert(helper: BaseViewHolder, item: VodBean) {
            helper.setVisible(R.id.item_tv_card_child_tip, false)
            helper.setText(R.id.item_tv_card_child_title, item.vodName)
            helper.setText(R.id.item_tv_card_child_up_title, item.vodRemarks)
            var img = item.vod_pic

            var icon = helper.getView<ImageView>(R.id.item_iv_card_child_icon)
            var lp = icon.layoutParams
            val perWidth = (getScreenWidth(LitePalApplication.getContext()) - dp2px(LitePalApplication.getContext(), 4f)) / 3
            lp.height = (perWidth * 1.4f).toInt()
            icon.layoutParams = lp
            Glide.with(helper.itemView.context)
                    .load(img)
                    .thumbnail(0.1f)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(icon)
        }
    }

    inner class SelectionAdapter : BaseQuickAdapter<UrlBean, BaseViewHolder>(R.layout.item_video_source) {

        override fun convert(helper: BaseViewHolder, item: UrlBean) {
            if (mVodBean.type_id == 3) {
                helper.itemView.layoutParams = helper.itemView.layoutParams.apply {
                    width = ConvertUtils.dp2px(130f)
                    height = ConvertUtils.dp2px(50f)
                }
            } else {
                helper.itemView.layoutParams = helper.itemView.layoutParams.apply {
                    width = ConvertUtils.dp2px(50f)
                    height = ConvertUtils.dp2px(50f)
                }
            }
            val position = helper.layoutPosition
            if (position == urlIndex) {
                helper.setTextColor(R.id.tv, ColorUtils.getColor(R.color.userTopBg))
            } else {
                helper.setTextColor(R.id.tv, ColorUtils.getColor(R.color.gray_999))
            }
            var name = item.name.replace("第", "").replace("集", "")
            helper.setText(R.id.tv, name)
        }
    }

    companion object {
        @JvmField
        val VOD_BEAN = "vodBean"

        @JvmField
        val URL_INDEX = "urlIndex"

        @JvmField
        val PLAY_SOURCE_INDEX = "playInfoIndex"

        @JvmStatic
        fun newInstance(vodBean: VodBean, urlIndex: Int, playSourceIndex: Int): VideoDetailFragment = VideoDetailFragment().apply {
            arguments = Bundle().apply {
                putParcelable(VOD_BEAN, vodBean)
                putInt(URL_INDEX, urlIndex)
                putInt(PLAY_SOURCE_INDEX, playSourceIndex)
            }
        }
    }
}
