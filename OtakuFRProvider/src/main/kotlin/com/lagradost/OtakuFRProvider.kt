package com.lagradost


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element

import me.xdrop.fuzzywuzzy.FuzzySearch
import java.util.*
import kotlin.collections.ArrayList

class OtakuFRProvider : MainAPI() {
    override var mainUrl = "https://otakufr.co/"
    override var name = "OtakuFR"
    override val hasQuickSearch = false // recherche rapide (optionel, pas vraimet utile)
    override val hasMainPage = true // page d'accueil (optionel mais encoragé)
    override var lang = "fr" // fournisseur est en francais
    override val supportedTypes =
        setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA) // animes, animesfilms

    private val nCharQuery = 10 // take the lenght of the query + nCharQuery
    private val resultsSearchNbr = 50 // take only n results from search function


    /**
    Cherche le site pour un titre spécifique

    La recherche retourne une SearchResponse, qui peut être des classes suivants: AnimeSearchResponse, MovieSearchResponse, TorrentSearchResponse, TvSeriesSearchResponse
    Chaque classes nécessite des données différentes, mais a en commun le nom, le poster et l'url
     **/
    override suspend fun search(query: String): List<SearchResponse> {
        val link =
            "$mainUrl/toute-la-liste-affiches/?q=$query" // search'
        val document =
            app.post(link).document // app.get() permet de télécharger la page html avec une requete HTTP (get)
        val results = document.select("div.list > >article")

        val allresultshome =
            results.mapNotNull { article ->  // avec mapnotnull si un élément est null, il sera automatiquement enlevé de la liste
                article.toSearchResponse()
            }
        return allresultshome

    }

    /**
     * charge la page d'informations, il ya toutes les donées, les épisodes, le résumé etc ...
     * Il faut retourner soit: AnimeLoadResponse, MovieLoadResponse, TorrentLoadResponse, TvSeriesLoadResponse.
     */
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document //
        var mediaType = TvType.Anime
        val episode =
            document.select("div.list-episodes > a")
        val poster =
            document.select("article.my-3 > div.card-body > div.row >div.text-center> figure.m-0 > img")
                .attr("src")
        var title = document.select("div.list > div.title").text() //
        var dataUrl = ""
        var link_video = ""
        /////////////////////////////////////
        val episodes = episode.map { infoEpisode ->
            val remove = infoEpisode.select("span").text().toString()
            val title = infoEpisode.text().replace(remove, "")
            val textEpisode =
                Regex(""".* (\d*) [VvfF]{1,1}""")
            val episodeNum = textEpisode.find(title)?.groupValues?.get(1)?.toInt()
            link_video = infoEpisode.attr("href")
            val link_poster = poster
            dataUrl = link_video
            Episode(
                link_video,
                episode = episodeNum,
                name = title,
                posterUrl = link_poster

            )


        }
        var infotext = document.selectFirst("ul.list-unstyled")?.text()
        val infosListRegex = Regex("""Type\: ([fF]ilm)""")
        val infosList = infotext?.let { infosListRegex.find(it)?.groupValues?.get(1) }
        val isinfosList = !infosList.isNullOrBlank()
        if (isinfosList) {
            if (infosList!!.contains("ilm")) mediaType = TvType.AnimeMovie
        }

        val description = document.selectFirst("div.synopsis > p")?.text()


        if (mediaType == TvType.AnimeMovie) {
            return newMovieLoadResponse(
                title,
                url,
                mediaType,
                dataUrl
            ) { // retourne les informations du film
                this.posterUrl = poster
                this.plot = description
            }
        } else  // an anime
        {
            return newAnimeLoadResponse(
                title,
                url,
                mediaType,
            ) {
                this.posterUrl = poster
                this.plot = description
                addEpisodes(
                    DubStatus.Subbed,
                    episodes.reversed()
                )
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
        val url = data
        val document = app.get(url).document
        val allLinkstoembed = document.select("div#nav-tabContent > div")

        val headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest"
        )
        var regexurlEmbed = Regex("""data-url='(.*)' d""")
        allLinkstoembed.apmap { player ->
            var link = fixUrl(player.select("iframe").attr("src"))
            var playerUrl = fixUrl(
                regexurlEmbed.find(
                    app.get(
                        link,
                        headers = headers
                    ).text
                )?.groupValues?.get(1).toString()
            )

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

    private fun Element.toSearchResponse(): SearchResponse {
        val figure = select("div >div >figure")
        var posterUrl = figure.select(" a > img").attr("src")
        val title = select(" div >div > div > a").text()
        val link = figure.select("a").attr("href")

        return newAnimeSearchResponse(
            title,
            link,
            TvType.Anime,
            false,
        ) {
            this.posterUrl = posterUrl
            this.dubStatus = EnumSet.of(DubStatus.Subbed)
        }

    }

    private suspend fun Element.tomainHome(): SearchResponse {
        val figure = select("div >figure")
        var posterUrl = figure.select("a > img").attr("src")
        val title = select(" div > a").text()
        val url = select(" div > a").attr("href")
        val document = app.get(url).document
        val link = document.select("ol.breadcrumb > li:nth-child(2) > a").attr("href")

        return newAnimeSearchResponse(
            title,
            link,
            TvType.Anime,
            false,
        ) {
            this.posterUrl = posterUrl
            this.dubStatus = EnumSet.of(DubStatus.Subbed)
        }

    }

    private fun Element.toTopTen(): SearchResponse {
        val figure = select("figure")
        var posterUrl = figure.select("img").attr("src")
        val title = select("div.titles").text()
        val link = this.attr("href")

        return newAnimeSearchResponse(
            title,
            link,
            TvType.Anime,
            false,
        ) {
            this.posterUrl = posterUrl
            this.dubStatus = EnumSet.of(DubStatus.Subbed)
        }

    }

    override val mainPage = mainPageOf(
        Pair("$mainUrl", "Top 10 des animes de la semaine"),
        Pair("$mainUrl/page/", "Nouveaux épisodes"),
        Pair("$mainUrl/en-cours/page/", "Animes en cours"),
        Pair("$mainUrl/termine/page/", "Animes terminés"),
        Pair("$mainUrl/film/page/", "Films")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val categoryName = request.name
        var url = request.data + page
        var cssSelector = ""
        if (categoryName.contains("Top 10") && page <= 1) {
            cssSelector = "div.block-most-watched:nth-child(2) > div.list-group > a.list-group-item"
        }
        val document = app.get(url).document

        val home = when (!categoryName.isNullOrBlank()) {
            request.name.contains("Animes"), request.name.contains("Films") -> document.select("div.list > >article")
                .mapNotNull { article -> article.toSearchResponse() }
            request.name.contains("Top 10") -> document.select(cssSelector)
                .mapNotNull { item -> item.toTopTen() }

            else -> document.select("div.section-1 > article")
                .apmap { article -> article.tomainHome() }
        }

        return newHomePageResponse(categoryName, home)
    }


}