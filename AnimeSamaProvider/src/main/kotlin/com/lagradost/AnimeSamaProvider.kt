package com.lagradost


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson

import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element

import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.*
import kotlin.collections.ArrayList

class AnimeSamaProvider : MainAPI() {
    override var mainUrl = "https://anime-sama.fr"
    override var name = "Anime-sama"
    override val hasQuickSearch = false // recherche rapide (optionel, pas vraimet utile)
    override val hasMainPage = true // page d'accueil (optionel mais encoragé)
    override var lang = "fr" // fournisseur est en francais
    override val supportedTypes =
        setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA) // animes, animesfilms

    private val nCharQuery = 10 // take the lenght of the query + nCharQuery
    private val resultsSearchNbr = 50 // take only n results from search function


    data class EpisodeData(
        @JsonProperty("id") val id: Int,
        @JsonProperty("title") val title: String?,
        @JsonProperty("title_english") val title_english: String?,
        @JsonProperty("title_romanji") val title_romanji: String?,
        @JsonProperty("title_french") val title_french: String?,
        @JsonProperty("others") val others: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("popularity") val popularity: Int?,
        @JsonProperty("url") val url: String,
        @JsonProperty("genre") val genre: Genre?,
        @JsonProperty("url_image") val url_image: String?,
        @JsonProperty("score") val score: String?,
        @JsonProperty("start_date_year") val start_date_year: String?,
        @JsonProperty("nb_eps") val nb_eps: String?,

        )

    data class Genre(
        @JsonProperty("0") val action: String?,
        @JsonProperty("1") val adventure: String?,
        @JsonProperty("2") val drama: String?,
        @JsonProperty("3") val fantasy: String?,
        @JsonProperty("4") val military: String?,
        @JsonProperty("5") val shounen: String?,
    )


    /**
    Cherche le site pour un titre spécifique

    La recherche retourne une SearchResponse, qui peut être des classes suivants: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    Chaque classes nécessite des données différentes, mais a en commun le nom, le poster et l'url
     **/
    val allresultshome = ArrayList<SearchResponse>()
    override suspend fun search(query: String): List<SearchResponse> {
        allresultshome.clear()
        val link =
            "$mainUrl/search/search.php?terme=$query&s=Search" // search'
        val document =
            app.get(link).document // app.get() permet de télécharger la page html avec une requete HTTP (get)
        val results = document.select("div.search_text > div.bd-highlight")

        results.apmap { article ->  // avec mapnotnull si un élément est null, il sera automatiquement enlevé de la liste
            article.toSearchResponse1()
        }
        return allresultshome

    }

    val regexGetlink = Regex("""(http.*)\'\,""")
    private fun Element.toSearchResponse_all(posterUrl: String?): SearchResponse {

        val text = this.text()
        val title = text
        val link_on_click = this.attr("onclick")
        val link =
            regexGetlink.find(link_on_click)?.groupValues?.get(1) ?: throw ErrorLoadingException()
        var dubstatus = if (title.lowercase().contains("vostfr")) {
            EnumSet.of(DubStatus.Subbed)
        } else {
            EnumSet.of(DubStatus.Dubbed)
        }
        if (text.lowercase().contains("film")) {
            return newMovieSearchResponse(
                title,
                link,
                TvType.AnimeMovie,
                false,
            ) {
                this.posterUrl = posterUrl

            }

        } else  // an Anime
        {
            return newAnimeSearchResponse(
                title,
                link,
                TvType.Anime,
                false,
            ) {
                this.posterUrl = posterUrl
                this.dubStatus = dubstatus
            }
        }
    }

    private suspend fun Element.toSearchResponse1() {
        val figcaption = select(" div.media-body > div >a > h5").text()
        if (!figcaption.lowercase().trim().contains("scan")) {
            val posterUrl = select("div.bd-highlight >div >a>img ").attr("src")
            val link_to_anime = select("div >a").attr("href")
            val document =
                app.get(link_to_anime).document
            val all_anime = document.select("div.synsaisons > li")
            all_anime.forEach { saga -> allresultshome.add(saga.toSearchResponse_all(posterUrl)) }
        }

    }

    /**
     * charge la page d'informations, il ya toutes les donées, les épisodes, le résumé etc ...
     * Il faut retourner soit: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
     */
    private val scpritAllEpisode = "episodes.js"
    private val regexAllcontentEpisode =
        Regex("""\[[^\]]*]""")
    private val regexAllLinkepisode = Regex("""'[^']*',""")
    private val regexCreateEp =Regex("""for \(var i = ([+-]?(?=\.\d|\d)(?:\d+)?(?:\.?\d*))(?:[eE]([+-]?\d+))?""")
    override suspend fun load(url: String): LoadResponse {
        val episodes = ArrayList<Episode>()
        val episodesLink = ArrayList<String>()
        val url_scriptEp = if (url.takeLast(1) != "/") {
            "$url/$scpritAllEpisode"
        } else {
            "$url$scpritAllEpisode"
        }
        val getScript = app.get(url_scriptEp)
        val text_script = getScript.text
        val script =
            text_script
        val resultsAllContent = regexAllcontentEpisode.findAll(script)
        //////////////////////////////////////
        /////////////////////////////////////
        var idx_Ep = 0
        var idx_Epfirstcontent = 0
        var firstcontent = true
        var oldNbrContent = 0
        var link_poster = ""
        resultsAllContent.forEach { content_i ->
            val contentEpisodeLink = content_i.groupValues[0]
            val AllLinkEpisodeFromContent_i = regexAllLinkepisode.findAll(contentEpisodeLink)
            //AllLinkEpisodeFromContent_i.elementAt(0)
            val nbr_Ep = AllLinkEpisodeFromContent_i.count()
            AllLinkEpisodeFromContent_i.forEach { link ->
                // first content

                if (firstcontent && idx_Epfirstcontent < nbr_Ep) {
                    oldNbrContent = nbr_Ep
                    val link_scpt = link.groupValues[0]
                    episodesLink.add(link_scpt)
                } else {
                    firstcontent = false
                    if (nbr_Ep > oldNbrContent && idx_Ep >= oldNbrContent) {
                        val link_scpt = link.groupValues[0]
                        episodesLink.add(link_scpt)

                    } else {

                        val link_scpt = link.groupValues[0]
                        episodesLink[idx_Ep] = episodesLink[idx_Ep] + link_scpt
                    }
                    idx_Ep++
                }

                idx_Epfirstcontent++
                if (idx_Ep > (nbr_Ep - 1)) { // init idx_Ep
                    idx_Ep = 0
                }


            }

        }
        var episode_tite = ""//select#selectEps.episodes > option

        val html = app.get(url)
        val document = html.document
        val title =
            document.select("p.soustitreaccueil.syntitreanime").text()
        var all_title = document.select("select#selectEps.episodes > option")

        idx_Ep = regexCreateEp.find(html.text)?.groupValues?.get(1)?.toInt() ?: 1 // episode from 1

        episodesLink.forEach { link_video ->
            episode_tite ="Episode $idx_Ep"
            if (!all_title.isNullOrEmpty()) {
                episode_tite = all_title[idx_Ep - 1].text()
            }
           /* link_poster = when(!link_video.isNullOrBlank()){
                link_video.contains("video.sibnet.ru") -> app.get(url).document.select("[property=og:image]").attr("content")
                else -> ""
            }*/
            episodes.add(
                Episode(
                    link_video,
                    episode = idx_Ep,
                    name = episode_tite,
                    posterUrl = link_poster // document.select("[property=og:image]") pour

                )

            )
            idx_Ep++
        }


        val description = ""
        val poster = ""

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Anime,
        ) {
            this.posterUrl = poster
            this.plot = description
            addEpisodes(
                DubStatus.Dubbed,
                episodes
            )

        }

    }


    /** récupere les liens .mp4 ou m3u8 directement à partir du paramètre data généré avec la fonction load()**/
    val rgxGetLink = Regex("""'[^']*',""")
    override suspend fun loadLinks(
        data: String, // fournit par load()
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {

        val results = rgxGetLink.findAll(data)

        results.forEach { link ->

            var playerUrl = link.groupValues[0].replace("'","").replace(",","")

            if (!playerUrl.isNullOrBlank())
                loadExtractor(
                    httpsify(playerUrl),
                    playerUrl,
                    subtitleCallback
                ) { link ->
                    callback.invoke(
                        ExtractorLink(
                            link.source,
                            link.name + "",
                            link.url,
                            link.referer,
                            getQualityFromName("HD"),
                            link.isM3u8,
                            link.headers,
                            link.extractorData
                        )
                    )
                }
        }

        return true
    }

    private fun List<Element>.tryTofindLatestSeason(): Pair<String?, String?> {
        var link: String? = ""
        var i = 0
        var sum = 0
        var sumVost = 0
        var newSum = 0
        var newSumVost = 0
        var newSumMovie = 0
        var sumMovie = 0
        var text = ""
        var detect_anime_Vostfr: Boolean
        var detect_anime_fr: Boolean
        var isVostfr = false
        var isFR = false
        var dubStatus: String? = ""
        while (i < this.size) {

            text = this[i].text()
            var a = text.lowercase().contains("vostfr")
            var b = text.lowercase().contains("film")
            var c = text.lowercase().contains("oav")
            detect_anime_Vostfr = a && !b && !c
            detect_anime_fr = !a && !b && !c
            if (detect_anime_Vostfr) {
                isVostfr = true

                findAllNumber.findAll(text).forEach { number ->
                    //if (number.toString().length < 3) {
                    newSumVost += number.groupValues[1].toInt()
                    //}

                }
                if (newSumVost >= sumVost) {
                    sumVost = newSumVost
                    val link_on_click =
                        this[i].attr("onclick") ?: throw ErrorLoadingException()
                    link = regexGetlink.find(link_on_click)?.groupValues?.get(1)
                    dubStatus = "vostfr"
                }
            } else if (!isVostfr && detect_anime_fr) {
                isFR = true
                findAllNumber.findAll(text).forEach { number ->
                    // if (number.toString().length < 3) {
                    newSum += number.groupValues[1].toInt()
                    // }
                }
                if (newSum >= sum) {
                    sum = newSum
                    val link_on_click =
                        this[i].attr("onclick") ?: throw ErrorLoadingException()
                    link = regexGetlink.find(link_on_click)?.groupValues?.get(1)
                    dubStatus = "fr"
                }
            } else if (!isVostfr && !isFR) {
                findAllNumber.findAll(text).forEach { number ->
                    newSumMovie += number.groupValues[1].toInt()
                }
                if (newSumMovie >= sumMovie) {
                    sumMovie = newSumMovie
                    val link_on_click =
                        this[i].attr("onclick") ?: throw ErrorLoadingException()
                    link = regexGetlink.find(link_on_click)?.groupValues?.get(1)
                    dubStatus = "film"
                }
            }
            newSumMovie = 0
            newSumVost = 0
            newSum = 0
            i++
        }

        return (link to dubStatus)
    }

    val findAllNumber = Regex("""([0-9]+)""")
    private suspend fun Element.toSearchResponse(): SearchResponse? {
        val figcaption = select("a >figcaption > span").text()
        if (figcaption.lowercase().trim() != "scan") {
            val posterUrl = select("a > img").attr("src")
            //val type = figcaption.lowercase()

            val title = select("a >figcaption").text().replace("$figcaption", "")
            val global_link = select("a").attr("href")
            if (global_link.contains("search.php")) {
                return null
            }
            /*  if (global_link.contains("search.php")) {
                  val document =
                      app.get(global_link).document

                  var (link, dub) = document.select("div.synsaisons > li").tryTofindLatestSeason()
                  var dubstatus = if (dub.toString().lowercase().contains("vostfr")) {
                      EnumSet.of(DubStatus.Subbed)
                  } else {
                      EnumSet.of(DubStatus.Dubbed)
                  }
              } else {*/
            val document =
                app.get(global_link).document
            var (link, dub) = document.select("div.synsaisons > li").tryTofindLatestSeason()
            var dubstatus = if (dub.toString().lowercase().contains("vostfr")) {
                EnumSet.of(DubStatus.Subbed)
            } else {
                EnumSet.of(DubStatus.Dubbed)
            }
            val type = dub.toString()
            //}
            val isMovie = type.contains("film")
            if (isMovie) {
                return newMovieSearchResponse(
                    title,
                    link.toString(),
                    TvType.AnimeMovie,
                    false,
                ) {
                    this.posterUrl = posterUrl

                }

            } else  // an Anime
            {
                return newAnimeSearchResponse(
                    title,
                    link.toString(),
                    TvType.Anime,
                    false,
                ) {
                    this.posterUrl = posterUrl
                    this.dubStatus = dubstatus
                }
            }
        } else {
            return null
        }

    }


    override val mainPage = mainPageOf(
        Pair("$mainUrl", "A NE PAS RATER"),
        Pair("$mainUrl", "CLASSIQUE"),
        Pair("$mainUrl", "ANIMES FINIS"),
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categoryName = request.name

        var cssSelector = ""
        if (page <= 1) {
            cssSelector = "div.container-fluid>div#sectionsAccueil"
        }
        val url = request.data

        val document = app.get(url).document

        val home = when (!categoryName.isNullOrBlank()) {
            request.name.contains("FINIS") -> document.select("$cssSelector")[2].select("figure")
                .apmap { article -> article.toSearchResponse() }.mapNotNull { it -> it }
            request.name.contains("RATER") -> document.select("$cssSelector")[1].select("figure")
                .apmap { article -> article.toSearchResponse() }.mapNotNull { it -> it }
            else ->
                document.select("$cssSelector")[0].select("figure")
                    .apmap { article -> article.toSearchResponse() }.mapNotNull { it -> it }
        }
        return newHomePageResponse(request.name, home)
    }
}