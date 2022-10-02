package com.lagradost

import android.webkit.CookieManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import okhttp3.Interceptor
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.runBlocking

import java.lang.Math.ceil


class MacIPTVProvider : MainAPI() {
    override var mainUrl = "http://matrix-ott.tv:8080"
    var mainUrl0 = mainUrl
        //"http://matrix-ott.tv:8080" //"http://ultra-box.club/mac=00%3A1A%3A79%3A6C%3ACD%3AC8"
    private val mac_adresse ="mac=00:1A:79:A7:9E:ED"// "mac=00:1A:79:A7:9E:ED"
    override var name = "BoxIPTV"
    override val hasQuickSearch = false // recherche rapide (optionel, pas vraimet utile)
    override val hasMainPage = true // page d'accueil (optionel mais encoragé)
    override var lang = "fr" // fournisseur est en francais
    override val supportedTypes =
        setOf(TvType.Live) // live
    companion object {
        var companionName = "Nginx"
        var loginMac: String? = null
        var overrideUrl: String? = null
        const val ERROR_STRING = "No nginx url specified in the settings"
    }
    private fun getAuthHeader(): Map<String, String> {
        val url = overrideUrl ?: mainUrl0// "http://matrix-ott.tv:8080"
        mainUrl = url
        if (mainUrl == "NONE" || mainUrl.isNullOrBlank()) {
            mainUrl= mainUrl0//"http://matrix-ott.tv:8080"
        }

        val localCredentials = loginMac
        if (localCredentials == null || localCredentials.trim() == "") {
            return   mapOf(
                "Cookie" to mac_adresse,
                "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
            )
        }

       val headerMac =  mapOf(
           "Cookie" to localCredentials,
           "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
       )
        return headerMac
    }

   /* private val headerMac =
        mapOf(
            "Cookie" to mac_adresse,
            "User-Agent" to "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3",
        )*/// matrix-ott 00:1a:79:a7:9e:ed;June 20, 2023, 3:51 am


    private fun getCloseMatches(sequence: String, items: Collection<String>): List<String> {
        val a = sequence.trim().lowercase()

        return items.mapNotNull { item ->
            val b = item.trim().lowercase()
            if (b.contains(a))
                item
            else if (a.contains(b))
                item
            else null
        }
    }

    /**
    Cherche le site pour un titre spécifique

    La recherche retourne une SearchResponse, qui peut être des classes suivants: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    Chaque classes nécessite des données différentes, mais a en commun le nom, le poster et l'url
     **/
    override suspend fun search(query: String): List<SearchResponse> {
        val results = getCloseMatches(query, arraymediaPlaylist.map { it.title })
        val searchResutls = ArrayList<SearchResponse>()
        var count = 0
        for (media in arraymediaPlaylist) {
            if (count == results.size) {
                break
            }
            if (media.title == results[count]) {
                searchResutls.add(
                    LiveSearchResponse(
                        media.title,
                        mainUrl,
                        media.title,
                        TvType.Live,
                        media.url_image,
                    )
                )

                ++count
            }
        }

        return searchResutls

    }

    data class Root_epg(

        @JsonProperty("js") var js: ArrayList<Js_epg> = arrayListOf()

    )

    data class Js_epg(

        @JsonProperty("id") var id: String? = null,
        @JsonProperty("ch_id") var chId: String? = null,
        @JsonProperty("correct") var correct: String? = null,
        @JsonProperty("time") var time: String? = null,
        @JsonProperty("time_to") var timeTo: String? = null,
        @JsonProperty("duration") var duration: Int? = null,
        @JsonProperty("name") var name: String? = null,
        @JsonProperty("descr") var descr: String? = null,
        @JsonProperty("real_id") var realId: String? = null,
        @JsonProperty("category") var category: String? = null,
        @JsonProperty("director") var director: String? = null,
        @JsonProperty("actor") var actor: String? = null,
        @JsonProperty("start_timestamp") var startTimestamp: Int? = null,
        @JsonProperty("stop_timestamp") var stopTimestamp: Int? = null,
        @JsonProperty("t_time") var tTime: String? = null,
        @JsonProperty("t_time_to") var tTimeTo: String? = null,
        @JsonProperty("mark_memo") var markMemo: Int? = null,
        @JsonProperty("mark_archive") var markArchive: Int? = null

    )

    private fun getEpg(response: String): String {
        val reponseJSON_0 = tryParseJson<Root_epg>(response)
        var description = ""
        val epg_data = reponseJSON_0?.js?.forEach { epg_i ->
            var name = epg_i.name
            var descr = epg_i.descr
            var t_time = epg_i.tTime
            var t_time_to = epg_i.tTimeTo
            var new_descr = "De $t_time à $t_time_to - $name : $descr"
            if (!description.contains(new_descr)) {
                description = "$description\n $new_descr"
            }

        }
        return description
    }

    /**
     * Charge la page d'informations, il ya toutes les donées, les épisodes, le résumé etc ...
     * Il faut retourner soit: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
     */

    override suspend fun load(url: String): LoadResponse {
        CookieManager.getInstance().removeAllCookies(null)
        val showlist = ArrayList<Episode>()
        var link = ""
        var title = ""
        var posterUrl = ""
        var description = ""
        val header= getAuthHeader()
        for (media in arraymediaPlaylist) {
            val keyId = "/-${media.id}-"
            if (url.contains(keyId)) {
                val epg_url =
                    "$mainUrl/portal.php?type=itv&action=get_short_epg&ch_id=${media.ch_id}&size=10&JsHttpRequest=1-xml" // descriptif
                var response = app.get(epg_url, headers = header)
                description = getEpg(response.text)
                link = media.url
                title = media.title
                val a = title.lowercase().replace("fr", " ").replace(":", " ").trim()//
                posterUrl = media.url_image.toString()
                var b_new: String
                arraymediaPlaylist.apmap { channel ->
                    val b = channel.title.lowercase().replace("fr", " ").replace(":", " ").trim()//
                    b_new = b.take(4)
                    if (a.take(4).contains(b_new) && media.tv_genre_id == channel.tv_genre_id) {
                        val epg_url =
                            "$mainUrl/portal.php?type=itv&action=get_short_epg&ch_id=${channel.ch_id}&size=10&JsHttpRequest=1-xml" // descriptif
                        var response = app.get(epg_url, headers = header)
                        var description = getEpg(response.text)
                        val streamurl = channel.url
                        val channelname = channel.title
                        val posterurl = channel.url_image.toString()
                        showlist.add(
                            Episode(
                                streamurl,
                                channelname,
                                null,
                                null,
                                posterurl,
                                null,
                                description
                            )
                        )
                    }

                }

                break
            }

        }
        if (showlist.size >= 2) {
            return TvSeriesLoadResponse(
                title,
                url,
                this.name,
                TvType.Live,
                showlist,
                posterUrl
            )
        } else {
            return LiveStreamLoadResponse(
                name = title,
                url = link,
                apiName = this.name,
                dataUrl = link,
                posterUrl = posterUrl,
                //year = null,
                plot = description
            )
        }
    }

    /** get the channel id */
    val regexCode_ch = Regex("""\/(\d*)\?""")

    /**
     * Use new token.
     * */
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        // Needs to be object instead of lambda to make it compile correctly
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request()
                if (request.url.toString().contains("token")

                ) {
                    val chID = regexCode_ch.find(request.url.toString())!!.groupValues[1] + "_"
                    val TokenLink =
                        "$mainUrl/portal.php?type=itv&action=create_link&cmd=ffmpeg%20http://localhost/ch/$chID&series=&forced_storage=0&disable_ad=0&download=0&force_ch_link_check=0&JsHttpRequest=1-xml"

                    var link = ""
                    runBlocking {
                        val header= getAuthHeader()
                        val getTokenLink = app.get(TokenLink, headers = header).text
                        val regexGetLink = Regex("""(http.*)\"\},""")
                        link = regexGetLink.find(getTokenLink)?.groupValues?.get(1).toString()
                            .replace(
                                """\""",
                                ""
                            )
                    }

                    val newRequest = chain.request()
                        .newBuilder().url(link).build()
                    return chain.proceed(newRequest)
                } else {
                    return chain.proceed(chain.request())
                }
            }
        }
    }

    /** récupere les liens .mp4 ou m3u8 directement à partir du paramètre data généré avec la fonction load()**/
    override suspend fun loadLinks(
        data: String, // fournit par load()
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {

        val header= getAuthHeader()
        val TokenLink =
            "$mainUrl/portal.php?type=itv&action=create_link&cmd=ffmpeg%20$data&series=&forced_storage=0&disable_ad=0&download=0&force_ch_link_check=0&JsHttpRequest=1-xml"
        val getTokenLink = app.get(TokenLink, headers = header).text
        val regexGetLink = Regex("""(http.*)\"\},""")
        val link =
            regexGetLink.find(getTokenLink)?.groupValues?.get(1).toString().replace("""\""", "")

        callback.invoke(
            ExtractorLink(
                name,
                name,
                link,
                mainUrl,
                Qualities.Unknown.value,
                isM3u8 = false,
            )
        )

        return true
    }


    private val arraymediaPlaylist = ArrayList<channel>()

    data class channel(
        var title: String,
        var url: String,
        val url_image: String?,
        val lang: String?,
        var id: String?,
        var tv_genre_id: String?,
        var ch_id: String?,
    )

    data class Cmds(

        @JsonProperty("id") var id: String? = null,
        @JsonProperty("ch_id") var chId: String? = null,
        @JsonProperty("priority") var priority: String? = null,
        @JsonProperty("url") var url: String? = null,
        @JsonProperty("status") var status: String? = null,
        @JsonProperty("use_http_tmp_link") var useHttpTmpLink: String? = null,
        @JsonProperty("wowza_tmp_link") var wowzaTmpLink: String? = null,
        @JsonProperty("user_agent_filter") var userAgentFilter: String? = null,
        @JsonProperty("use_load_balancing") var useLoadBalancing: String? = null,
        @JsonProperty("changed") var changed: String? = null,
        @JsonProperty("enable_monitoring") var enableMonitoring: String? = null,
        @JsonProperty("enable_balancer_monitoring") var enableBalancerMonitoring: String? = null,
        @JsonProperty("nginx_secure_link") var nginxSecureLink: String? = null,
        @JsonProperty("flussonic_tmp_link") var flussonicTmpLink: String? = null

    )

    data class Data(

        @JsonProperty("id") var id: String? = null,
        @JsonProperty("name") var name: String? = null,
        @JsonProperty("number") var number: String? = null,
        @JsonProperty("censored") var censored: Int? = null,
        @JsonProperty("cmd") var cmd: String? = null,
        @JsonProperty("cost") var cost: String? = null,
        @JsonProperty("count") var count: String? = null,
        @JsonProperty("status") var status: Int? = null,
        @JsonProperty("hd") var hd: String? = null,
        @JsonProperty("tv_genre_id") var tvGenreId: String? = null,
        @JsonProperty("base_ch") var baseCh: String? = null,
        @JsonProperty("xmltv_id") var xmltvId: String? = null,
        @JsonProperty("service_id") var serviceId: String? = null,
        @JsonProperty("bonus_ch") var bonusCh: String? = null,
        @JsonProperty("volume_correction") var volumeCorrection: String? = null,
        @JsonProperty("mc_cmd") var mcCmd: String? = null,
        @JsonProperty("enable_tv_archive") var enableTvArchive: Int? = null,
        @JsonProperty("wowza_tmp_link") var wowzaTmpLink: String? = null,
        @JsonProperty("wowza_dvr") var wowzaDvr: String? = null,
        @JsonProperty("use_http_tmp_link") var useHttpTmpLink: String? = null,
        @JsonProperty("monitoring_status") var monitoringStatus: String? = null,
        @JsonProperty("enable_monitoring") var enableMonitoring: String? = null,
        @JsonProperty("enable_wowza_load_balancing") var enableWowzaLoadBalancing: String? = null,
        @JsonProperty("cmd_1") var cmd1: String? = null,
        @JsonProperty("cmd_2") var cmd2: String? = null,
        @JsonProperty("cmd_3") var cmd3: String? = null,
        @JsonProperty("logo") var logo: String? = null,
        @JsonProperty("correct_time") var correctTime: String? = null,
        @JsonProperty("nimble_dvr") var nimbleDvr: String? = null,
        @JsonProperty("allow_pvr") var allowPvr: Int? = null,
        @JsonProperty("allow_local_pvr") var allowLocalPvr: Int? = null,
        @JsonProperty("allow_remote_pvr") var allowRemotePvr: Int? = null,
        @JsonProperty("modified") var modified: String? = null,
        @JsonProperty("allow_local_timeshift") var allowLocalTimeshift: String? = null,
        @JsonProperty("nginx_secure_link") var nginxSecureLink: String? = null,
        @JsonProperty("tv_archive_duration") var tvArchiveDuration: Int? = null,
        @JsonProperty("locked") var locked: Int? = null,
        @JsonProperty("lock") var lock: Int? = null,
        @JsonProperty("fav") var fav: Int? = null,
        @JsonProperty("archive") var archive: Int? = null,
        @JsonProperty("genres_str") var genresStr: String? = null,
        @JsonProperty("cur_playing") var curPlaying: String? = null,
        @JsonProperty("epg") var epg: ArrayList<String> = arrayListOf(),
        @JsonProperty("open") var open: Int? = null,
        @JsonProperty("cmds") var cmds: ArrayList<Cmds> = arrayListOf(),
        @JsonProperty("use_load_balancing") var useLoadBalancing: Int? = null,
        @JsonProperty("pvr") var pvr: Int? = null

    )

    data class Js(

        @JsonProperty("total_items") var totalItems: Int? = null,
        @JsonProperty("max_page_items") var maxPageItems: Int? = null,
        @JsonProperty("selected_item") var selectedItem: Int? = null,
        @JsonProperty("cur_page") var curPage: Int? = null,
        @JsonProperty("data") var data: ArrayList<Data> = arrayListOf()

    )

    data class JsonGetGenre(

        @JsonProperty("js") var js: ArrayList<Js_category> = arrayListOf()

    )

    data class Js_category(

        @JsonProperty("id") var id: String? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("alias") var alias: String? = null,
        @JsonProperty("active_sub") var activeSub: Boolean? = null,
        @JsonProperty("censored") var censored: Int? = null

    )

    data class Root(

        @JsonProperty("js") var js: Js? = Js()

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val arrayHomepage = arrayListOf<HomePageList>()
        val header= getAuthHeader()
        val url =
            "$mainUrl/portal.php?type=itv&action=get_genres&JsHttpRequest=1-xml"//&force_ch_link_check=&JsHttpRequest=1-xml
        var response_0 = app.get(url, headers = header)
        val reponseJSON_0 = response_0.parsed<JsonGetGenre>()
        reponseJSON_0.js.apmap { js ->
            val idGenre = js.id
            val categoryTitle = js.title.toString()

            if (idGenre!!.contains("""\d""".toRegex()) && categoryTitle.contains("FR")) {
                var page_i = 1;
                val url =
                    "$mainUrl/portal.php?type=itv&action=get_ordered_list&genre=$idGenre&force_ch_link_check=&fav=0&sortby=number&hd=0&p=$page_i&JsHttpRequest=1-xml&from_ch_id=0"
                var response = app.get(url, headers = header, timeout = 3)
                var reponseJSON = response.parsed<Root>()
                val total_items = reponseJSON?.js?.totalItems
                val max_page_items = reponseJSON?.js?.maxPageItems?.toDouble()
                val pages = ceil(total_items!!.toDouble() / max_page_items!!.toDouble()).toInt()
                while (page_i <= pages) {
                    val data = reponseJSON?.js?.data
                    data?.forEach { value ->
                        val name = value.name.toString()
                        val tv_genre_id = value.tvGenreId
                        //&& b.contains(a) || a.contains(b)
                        val idCH = value.id
                        val link = "http://localhost/ch/$idCH" + "_"
                        val logo = value.logo?.replace("""\""", "")
                        val ch_id = value.cmds[0].chId
                        arraymediaPlaylist.add(
                            channel(
                                name,
                                link,
                                logo,
                                "",
                                idCH,
                                tv_genre_id,
                                ch_id
                            )
                        )

                    }
                    page_i++
                    val url =
                        "$mainUrl/portal.php?type=itv&action=get_ordered_list&genre=$idGenre&force_ch_link_check=&fav=0&sortby=number&hd=0&p=$page_i&JsHttpRequest=1-xml&from_ch_id=0"
                    response = app.get(url, headers = header, timeout = 3)
                    reponseJSON = if (response.text.takeLast(2) != "}}") {
                        tryParseJson<Root>("${response.text}}") ?: response.parsed()
                    } else {
                        response.parsed()
                    }
                }
            }
            /***************************************** */

            val groupMedia = ArrayList<String>()
            var b_new: String
            var newgroupMedia: Boolean
            val home = arraymediaPlaylist.mapNotNull { media ->
                val b = media.title.lowercase().replace("fr", " ").replace(":", " ").trim()//
                b_new = b.take(4)
                newgroupMedia = true
                for (nameMedia in groupMedia) {
                    if (nameMedia.contains(b_new)) {
                        newgroupMedia = false
                        break
                    }
                }
                groupMedia.contains(b_new)
                if (page == 1 && (media.tv_genre_id == idGenre) && newgroupMedia
                ) {
                    groupMedia.add(b_new)
                    val groupName =
                        media.title.replace("""\s\d""".toRegex(), "").replace("""FHD""", "")
                            .replace("""UHD""", "").replace(""":""", "").replace("""FR """, "")
                            .replace("""HD""", "").trim()
                    LiveSearchResponse(
                        groupName,
                        "$mainUrl/-${media.id}-",
                        name,
                        TvType.Live,
                        media.url_image + "?w=10&h=10",
                    )
                } else {
                    null
                }
            }
            if (categoryTitle.contains("FR")) {
                var nameGenre = categoryTitle + " \uD83C\uDDE8\uD83C\uDDF5"
                nameGenre = nameGenre.replace("FR ", "").trim()
                arrayHomepage.add(HomePageList(nameGenre, home, isHorizontalImages = true))
            }

        }
        return HomePageResponse(
            arrayHomepage
        )
    }
}
