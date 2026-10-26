package com.daveendev.justdoodles

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play's side of PLUS: buying it, finding it again after a reinstall, and taking it away
 * again after a refund. It all goes through Google Play Billing, which talks to the Play Store app
 * on the phone, so the app itself never uses the internet and there's no server. [Plus] keeps
 * what Play last said, so PLUS works offline; when Play can't be reached, nothing changes.
 * The sideloaded build's version is in `src/direct`.
 */
class PlusStore {
    /** True: this build sells PLUS, so the grown-up settings show the PLUS page. */
    val available = true

    /** PLUS's price as Google Play shows it, in the phone's own currency ("₱79.00"). Null until Play has said. */
    var price: String? = null
        private set(value) {
            if (value == field) return
            field = value
            onStateChange?.invoke()
        }

    var state = PlusState.NOT_OWNED
        private set(value) {
            if (value == field) return
            field = value
            onStateChange?.invoke()
        }

    /** What the last [buy] or [restore] came to, for the PLUS page to put into words. Null before either. */
    var lastResult: PlusResult? = null
        private set(value) {
            field = value
            onStateChange?.invoke()
        }

    /** Called on the main thread when [state], [price] or [lastResult] changes. */
    var onStateChange: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var client: BillingClient? = null
    private var product: ProductDetails? = null
    /** Where to open Play's purchase sheet once [product] has loaded. */
    private var buyWhenLoaded: Activity? = null

    fun start(activity: Activity) {
        state = if (Plus.isOn) PlusState.OWNED else PlusState.NOT_OWNED
        client = BillingClient.newBuilder(activity.applicationContext)
            .setListener { result, purchases -> onMain { onPurchasesUpdated(result, purchases) } }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    /**
     * Asks Play again whenever the app comes back: catches a payment that went through, a purchase
     * made on another phone, and a refund. It's a call to the Play Store app on the phone, so it's cheap.
     */
    fun refresh() {
        val c = client ?: return
        when (c.connectionState) {
            BillingClient.ConnectionState.CONNECTED -> {
                queryPurchases()
                if (product == null) queryProductDetails()
            }
            BillingClient.ConnectionState.DISCONNECTED -> connect()
            else -> Unit // Connecting: it asks once it's through.
        }
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        client?.endConnection()
        client = null
        buyWhenLoaded = null
    }

    /** Opens Google Play's own purchase sheet. Only the PLUS page calls this, behind the grown-up gate. */
    fun buy(activity: Activity) {
        val c = client ?: return
        val details = product
        if (details != null) return launch(activity, details)
        buyWhenLoaded = activity
        when (c.connectionState) {
            BillingClient.ConnectionState.CONNECTED -> queryProductDetails()
            BillingClient.ConnectionState.DISCONNECTED -> connect()
            else -> Unit
        }
    }

    /** Asks Play for a PLUS bought earlier on this Google account. [onDone] says whether it found one. */
    fun restore(onDone: (Boolean) -> Unit) {
        queryPurchases { owned, code ->
            lastResult = when (owned) {
                true -> PlusResult.RESTORED
                false -> PlusResult.NOT_FOUND
                null -> resultFor(code)
            }
            onDone(owned == true)
        }
    }

    private fun connect() {
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) = onMain {
                if (result.responseCode == BillingResponseCode.OK) {
                    queryPurchases()
                    queryProductDetails()
                } else {
                    // Play is missing, signed out or busy. The cached answer stands; refresh() tries again.
                    if (result.responseCode == BillingResponseCode.BILLING_UNAVAILABLE) state = PlusState.UNAVAILABLE
                    if (buyWhenLoaded != null) {
                        buyWhenLoaded = null
                        fail(result.responseCode)
                    }
                }
            }

            // Auto reconnection is on, so the next call to Play reconnects by itself.
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /**
     * Asks Play what this Google account owns, and switches PLUS to match. This is also how a reinstall
     * gets PLUS back with no tap, and how a refund takes it away. [onDone] gets whether PLUS is owned,
     * or null and Play's response code when Play couldn't say: then nothing changes.
     */
    private fun queryPurchases(onDone: (owned: Boolean?, code: Int) -> Unit = { _, _ -> }) {
        val c = client ?: return onDone(null, BillingResponseCode.SERVICE_DISCONNECTED)
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        c.queryPurchasesAsync(params) { result, purchases ->
            onMain {
                if (result.responseCode != BillingResponseCode.OK) return@onMain onDone(null, result.responseCode)
                val wasPending = state == PlusState.PENDING
                settle(purchases.firstOrNull { PRODUCT_ID in it.products })
                if (wasPending && state == PlusState.OWNED) lastResult = PlusResult.BOUGHT
                onDone(state == PlusState.OWNED, result.responseCode)
            }
        }
    }

    /** Switches PLUS by what Play says about [purchase]. Null means this Google account doesn't own it. */
    private fun settle(purchase: Purchase?) {
        when (purchase?.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                state = PlusState.OWNED
                Plus.set(true)
                if (!purchase.isAcknowledged) acknowledge(purchase)
            }
            Purchase.PurchaseState.PENDING -> {
                state = PlusState.PENDING
                Plus.set(false)
            }
            // Never bought, or refunded and revoked.
            null -> {
                state = PlusState.NOT_OWNED
                Plus.set(false)
            }
            else -> Unit // A state Play doesn't explain: leave things as they are.
        }
    }

    /** Play refunds a purchase that isn't acknowledged within 3 days. If this fails, the next [queryPurchases] tries again. */
    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        client?.acknowledgePurchase(params) { }
    }

    /** Loads PLUS's product from Play, for its price and for the purchase sheet. */
    private fun queryProductDetails() {
        val c = client ?: return
        val plus = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(plus)).build()
        c.queryProductDetailsAsync(params) { result, details ->
            onMain {
                val found = details.productDetailsList.firstOrNull()
                if (found != null) {
                    product = found
                    price = found.oneTimePurchaseOfferDetails?.formattedPrice
                }
                val activity = buyWhenLoaded ?: return@onMain
                buyWhenLoaded = null
                when {
                    found != null -> launch(activity, found)
                    // Play answered but doesn't know the product: not set up in Play Console yet.
                    result.responseCode == BillingResponseCode.OK -> lastResult = PlusResult.FAILED
                    else -> fail(result.responseCode)
                }
            }
        }
    }

    private fun launch(activity: Activity, details: ProductDetails) {
        val c = client ?: return
        if (activity.isFinishing) return
        val item = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        details.oneTimePurchaseOfferDetails?.offerToken?.let { item.setOfferToken(it) }
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item.build())).build()
        val result = c.launchBillingFlow(activity, params)
        if (result.responseCode != BillingResponseCode.OK) onPurchasesUpdated(result, null)
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> {
                val plus = purchases?.firstOrNull { PRODUCT_ID in it.products } ?: return
                settle(plus)
                lastResult = when (state) {
                    PlusState.OWNED -> PlusResult.BOUGHT
                    PlusState.PENDING -> PlusResult.PENDING
                    else -> PlusResult.FAILED
                }
            }
            // Bought before, maybe on another phone: it's really a restore.
            BillingResponseCode.ITEM_ALREADY_OWNED -> restore { }
            else -> fail(result.responseCode)
        }
    }

    private fun fail(code: Int) {
        if (code == BillingResponseCode.BILLING_UNAVAILABLE) state = PlusState.UNAVAILABLE
        lastResult = resultFor(code)
    }

    private fun resultFor(code: Int) = when (code) {
        BillingResponseCode.USER_CANCELED -> PlusResult.CANCELED
        BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.NETWORK_ERROR,
        BillingResponseCode.SERVICE_DISCONNECTED -> PlusResult.NO_CONNECTION
        BillingResponseCode.BILLING_UNAVAILABLE -> PlusResult.NOT_AVAILABLE
        else -> PlusResult.FAILED
    }

    /** Play's answers can arrive on its own threads; PLUS and the screen only change on the main one. Nothing runs after [stop]. */
    private fun onMain(block: () -> Unit) {
        main.post { if (client != null) block() }
    }

    companion object {
        /** The one-time product in Play Console. Never consumed, so Play keeps it on the Google account for good. */
        const val PRODUCT_ID = "plus_lifetime"
    }
}
