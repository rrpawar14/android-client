package com.mifos.mifosxdroid.online.clientlist

import android.content.Context
import com.mifos.api.datamanager.DataManagerClient
import com.mifos.api.datamanager.DataManagerOffices
import com.mifos.mifosxdroid.R
import com.mifos.mifosxdroid.base.BasePresenter
import com.mifos.mifosxdroid.injection.ActivityContext
import com.mifos.objects.client.Client
import com.mifos.objects.client.Page
import com.mifos.objects.templates.clients.OfficeOptions
import com.mifos.utils.EspressoIdlingResource
import rx.Observable
import rx.Subscriber
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subscriptions.CompositeSubscription
import java.util.*
import javax.inject.Inject

/**
 * Created by Rajan Maurya on 6/6/16.
 * This Presenter Holds the All Logic to request to DataManagerClient and DataManagerClient, Take
 * care of that From Where Data will come Database or REST API.
 */
class ClientListPresenter @Inject constructor(private val mDataManagerClient: DataManagerClient, private val mDataManagerOffices: DataManagerOffices, @param:ActivityContext private val c: Context) : BasePresenter<ClientListMvpView?>() {
    private var mSubscriptions: CompositeSubscription? = null
    private var mDbClientList: List<Client>
    private var mSyncClientList: List<Client>
    private var mNewOfficeClientList: List<Client>
    private var officeId = 1
    private var loadmore = false
    private var mRestApiClientSyncStatus = false
    private var mDatabaseClientSyncStatus = false
    private var filterSearch = false
    override fun attachView(mvpView: ClientListMvpView?) {
        super.attachView(mvpView)
        mSubscriptions = CompositeSubscription()
    }

    override fun detachView() {
        super.detachView()
        mSubscriptions!!.unsubscribe()
    }

    /**
     * Loading Client List from Rest API and setting loadmore status
     *
     * @param loadmore Status, need ClientList page other then first.
     * @param offset   Index from Where ClientList will be fetched.
     */
    fun loadClients(loadmore: Boolean, offset: Int) {
        this.loadmore = loadmore
        if (!filterSearch) {
            loadClients(false, offset, officeId)
        } else {
            loadClientsByOfficeId(false, offset, officeId)
        }
    }

    /**
     * Showing Client List in View, If loadmore is true call showLoadMoreClients(...) and else
     * call showClientList(...).
     */
    fun showClientList(clients: List<Client>?) {
        if (loadmore) {
            mvpView!!.showLoadMoreClients(clients)
        } else {
            mvpView!!.showClientList(clients)
        }
    }

    /**
     * This Method will called, when Parent (Fragment or Activity) will be true.
     * If Parent Fragment is true there is no need to fetch ClientList, Just show the Parent
     * (Fragment or Activity) ClientList in View
     *
     * @param clients List<Client></Client>>
     */
    fun showParentClients(clients: List<Client>) {
        mvpView!!.unregisterSwipeAndScrollListener()
        if (clients.size == 0) {
            mvpView!!.showEmptyClientList(R.string.client)
        } else {
            mRestApiClientSyncStatus = true
            mSyncClientList = clients
            setAlreadyClientSyncStatus()
        }
    }

    /**
     * Setting ClientSync Status True when mRestApiClientSyncStatus && mDatabaseClientSyncStatus
     * are true.
     */
    fun setAlreadyClientSyncStatus() {
        if (mRestApiClientSyncStatus && mDatabaseClientSyncStatus) {
            showClientList(checkClientAlreadySyncedOrNot(mSyncClientList))
        }
    }

    /**
     * This Method fetching Client List from Rest API.
     *
     * @param paged  True Enabling the Pagination of the API
     * @param offset Value give from which position Fetch ClientList
     * @param limit  Maximum size of the Center
     */
    fun loadClients(paged: Boolean, offset: Int, officeId: Int) {
        EspressoIdlingResource.increment() // App is busy until further notice.
        checkViewAttached()
        mvpView!!.showProgressbar(true)
        mSubscriptions!!.add(mDataManagerClient.getAllClientsByOfficeId(paged, offset, officeId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(object : Subscriber<Page<Client?>?>() {
                    override fun onCompleted() {
                        loadOffices()
                    }

                    override fun onError(e: Throwable) {
                        mvpView!!.showProgressbar(false)
                        if (loadmore) {
                            mvpView!!.showMessage(R.string.failed_to_load_client)
                        } else {
                            mvpView!!.showError()
                        }
                        EspressoIdlingResource.decrement() // App is idle.
                    }

                    override fun onNext(clientPage: Page<Client?>?) {
                        mSyncClientList = clientPage?.pageItems as List<Client>


                        /*  if (mSyncClientList.size() == 0 && !loadmore) {
                            getMvpView().showEmptyClientList(R.string.client);
                            getMvpView().unregisterSwipeAndScrollListener();
                        } else if (mSyncClientList.size() == 0 && loadmore) {
                            getMvpView().showMessage(R.string.no_more_clients_available);
                        } else {
                            mRestApiClientSyncStatus = true;
                            setAlreadyClientSyncStatus();
                        }*/mRestApiClientSyncStatus = true
                        // setAlreadyClientSyncStatus();
                        mvpView!!.showClientList(mSyncClientList)
                        mvpView!!.showProgressbar(false)
                        EspressoIdlingResource.decrement() // App is idle.
                    }
                }))
    }

    fun loadClientsByOfficeId(paged: Boolean, offset: Int, officeId: Int?) {
        if (officeId != null) {
            this.officeId = officeId
        }
        filterSearch = true
        EspressoIdlingResource.increment() // App is busy until further notice.
        checkViewAttached()
        mvpView!!.showProgressbar(true)
        mSubscriptions!!.add(mDataManagerClient.getAllClientsByOfficeId(paged, offset, officeId!!)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(object : Subscriber<Page<Client?>?>() {
                    override fun onCompleted() {}
                    override fun onError(e: Throwable) {
                        mvpView!!.showProgressbar(false)
                        if (loadmore) {
                            mvpView!!.showMessage(R.string.failed_to_load_client)
                        } else {
                            mvpView!!.showError()
                        }
                        EspressoIdlingResource.decrement() // App is idle.
                    }

                    override fun onNext(clientPage: Page<Client?>?) {
                        if (offset < 8) {
                            println("offsetlessthan$offset")
                            mNewOfficeClientList = clientPage?.pageItems as List<Client>
                            mvpView!!.showClientList(mNewOfficeClientList)
                            mvpView!!.showProgressbar(false)
                            EspressoIdlingResource.decrement() // App is idle.
                        } else {
                            println("offsetgreaterthan$offset")
                            mSyncClientList = clientPage?.pageItems as List<Client>
                            if (mSyncClientList.size == 0 && !loadmore) {
                                mvpView!!.showEmptyClientList(R.string.client)
                                mvpView!!.unregisterSwipeAndScrollListener()
                            } else if (mSyncClientList.size == 0 && loadmore) {
                                mvpView!!.showMessage(R.string.no_more_clients_available)
                            } else {
                                mRestApiClientSyncStatus = true
                                //   setAlreadyClientSyncStatus();
                                mvpView!!.showClientList(mSyncClientList)
                                mvpView!!.showProgressbar(false)
                                EspressoIdlingResource.decrement() // App is idle.
                                //
                            }
                        }
                    }
                }))
    }

    fun loadOffices() {
        checkViewAttached()
        mSubscriptions!!.add(mDataManagerOffices.officesFields
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(object : Subscriber<List<OfficeOptions?>?>() {
                    override fun onCompleted() {}
                    override fun onError(e: Throwable) {
                        mvpView!!.showMessage(R.string.failed_to_fetch_offices)
                    }

                    override fun onNext(officeOptions: List<OfficeOptions?>?) {
                        mvpView!!.showOffices(officeOptions as List<OfficeOptions>)
                    }
                }))
    }

    fun createOfficeNameIdMap(offices: List<OfficeOptions>?,
                              officeNames: MutableList<String?>): HashMap<String, Int> {
        val officeMap = HashMap<String, Int>()
        officeMap[c.resources.getString(R.string.spinner_office)] = -1
        officeNames.clear()
        officeNames.add(c.resources.getString(R.string.spinner_office))
        Observable.from(offices)
                .subscribe { office ->
                    officeMap[office.name] = office.id
                    officeNames.add(office.name)
                }
        return officeMap
    }

    /**
     * This Method Loading the Client From Database. It request Observable to DataManagerClient
     * and DataManagerClient Request to DatabaseHelperClient to load the Client List Page from the
     * Client_Table and As the Client List Page is loaded DataManagerClient gives the Client List
     * Page after getting response from DatabaseHelperClient
     */
    fun loadDatabaseClients() {
        checkViewAttached()
        mSubscriptions!!.add(mDataManagerClient.allDatabaseClients
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(object : Subscriber<Page<Client?>?>() {
                    override fun onCompleted() {}
                    override fun onError(e: Throwable) {
                        mvpView!!.showMessage(R.string.failed_to_load_db_clients)
                    }

                    override fun onNext(clientPage: Page<Client?>?) {
                        mDatabaseClientSyncStatus = true
                        mDbClientList = clientPage?.pageItems as List<Client>
                        setAlreadyClientSyncStatus()
                    }
                })
        )
    }

    /**
     * This Method Filtering the Clients Loaded from Server is already sync or not. If yes the
     * put the client.setSync(true) and view will show those clients as sync already to user
     *
     * @param
     * @return Page<Client>
    </Client> */
    fun checkClientAlreadySyncedOrNot(clients: List<Client>): List<Client> {
        if (mDbClientList.size != 0) {
            for (dbClient in mDbClientList) {
                for (syncClient in clients) {
                    if (dbClient.id == syncClient.id) {
                        syncClient.isSync = true
                        break
                    }
                }
            }
        }
        return clients
    }

    companion object {
        private val LOG_TAG = ClientListPresenter::class.java.simpleName
    }

    init {
        mDbClientList = ArrayList()
        mSyncClientList = ArrayList()
        mNewOfficeClientList = ArrayList()
    }
}