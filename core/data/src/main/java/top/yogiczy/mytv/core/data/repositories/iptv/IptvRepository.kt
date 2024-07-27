package top.yogiczy.mytv.core.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.network.await
import top.yogiczy.mytv.core.data.repositories.FileCacheRepository
import top.yogiczy.mytv.core.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.core.data.utils.Logger

/**
 * 直播源数据获取
 */
class IptvRepository(
    private val sourceUrl: String,
) : FileCacheRepository("iptv.${sourceUrl.hashCode().toUInt().toString(16)}.txt") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 获取远程直播源数据
     */
    private suspend fun fetchSource(sourceUrl: String): String {
        log.d("获取远程直播源: $sourceUrl")

        val client = OkHttpClient()
        val request = Request.Builder().url(sourceUrl).build()

        try {
            val response = client.newCall(request).await()

            if (!response.isSuccessful) throw Exception("${response.code}: ${response.message}")

            return withContext(Dispatchers.IO) {
                response.body?.string() ?: ""
            }
        } catch (ex: Exception) {
            log.e("获取远程直播源失败", ex)
            throw Exception("获取远程直播源失败，请检查网络连接", ex)
        }
    }

    /**
     * 简化规则
     */
    private fun simplifyTest(group: ChannelGroup, channel: Channel): Boolean {
        return channel.name.lowercase().startsWith("cctv") || channel.name.endsWith("卫视")
    }

    /**
     * 获取直播源分组列表
     */
    suspend fun getChannelGroupList(
        cacheTime: Long,
        simplify: Boolean = false,
    ): ChannelGroupList {
        try {
            val sourceData = getOrRefresh(cacheTime) {
                fetchSource(sourceUrl)
            }

            val parser = IptvParser.instances.first { it.isSupport(sourceUrl, sourceData) }
            val groupList = parser.parse(sourceData)
            log.i("解析直播源完成：${groupList.size}个分组，${groupList.sumOf { it.channelList.size }}个频道")

            if (simplify) {
                return ChannelGroupList(groupList.map { group ->
                    group.copy(
                        channelList = ChannelList(
                            group.channelList.filter { iptv -> simplifyTest(group, iptv) },
                        )
                    )
                }.filter { it.channelList.isNotEmpty() })
            }

            return groupList
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }
}