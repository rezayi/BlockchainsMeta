package morez.blockchain.meta.extractor.swappers.curve

import morez.blockchain.meta.extractor.base.BaseMetaExtractor
import morez.blockchain.meta.model.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class CurveFinanceMetaExtractor(
    val restTemplate: RestTemplate
) : BaseMetaExtractor() {
    companion object {
        private const val SWAPPER_NAME = "CurveFi"
        private const val URL = "https://api.curve.fi/api/getPools/polygon/main"
        private val BLOCKCHAIN = Blockchain.POLYGON
    }

    override fun fetchMeta(): SwapperMeta {
        val tokensMap: MutableMap<TokenAddress, Token> = mutableMapOf()

        val config = restTemplate.getForObject(URL, CurveFinanceConfig::class.java)

        val routes = config?.data?.poolData
            ?.flatMap { pool ->
                val lpToken = tokensMap.getOrPut(pool.lpTokenAddress) {
                    pool.toToken(BLOCKCHAIN)
                }
                listOf(
                    getTokenToTokenPairs(pool, pool.coins, tokensMap),
                    getTokenToTokenPairs(pool, pool.underlyingCoins.orEmpty(), tokensMap),
                    getLpTokenToMembersPairs(pool, tokensMap, lpToken)
                ).flatten()
            }
            .orEmpty()

        return SwapperMeta(
            name = SWAPPER_NAME,
            type = SwapperType.Swapper,
            tokens = tokensMap.values.toList(),
            routes = routes
        )
    }

    private fun getLpTokenToMembersPairs(
        pool: CurveFinancePoolData,
        tokensMap: MutableMap<TokenAddress, Token>,
        lpToken: Token
    ) = pool.coins.flatMap { token ->
        val memberToken = tokensMap.getOrPut(token.address) {
            token.toToken(BLOCKCHAIN)
        }
        listOf(
            Route(
                srcToken = lpToken,
                dstToken = memberToken,
                routeMetaId = pool.id,
                routeInfo = mapOf(
                    "lpId" to pool.id,
                    "lpAddress" to pool.address
                )
            ),
            Route(
                srcToken = memberToken,
                dstToken = lpToken,
                routeMetaId = pool.id,
                routeInfo = mapOf(
                    "lpId" to pool.id,
                    "lpAddress" to pool.address
                )
            )
        )
    }

    private fun getTokenToTokenPairs(
        pool: CurveFinancePoolData,
        coins: List<CurveFinanceCoin>,
        tokensMap: MutableMap<TokenAddress, Token>
    ) = coins
        .flatMap { src ->
            val srcToken = tokensMap.getOrPut(src.address) {
                src.toToken(BLOCKCHAIN)
            }
            coins
                .filter { dst -> dst != src }
                .map { dst ->
                    val dstToken = tokensMap.getOrPut(dst.address) {
                        dst.toToken(BLOCKCHAIN)
                    }
                    Route(
                        srcToken = srcToken,
                        dstToken = dstToken,
                        routeMetaId = pool.id,
                        routeInfo = mapOf(
                            "lpId" to pool.id,
                            "lpAddress" to pool.address
                        )
                    )
                }
        }


}