/*
 * This project is licensed under the open source MPL V2.
 * See https://github.com/openMF/android-client/blob/master/LICENSE.md
 */
package com.mifos.mifosxdroid.online.clientlist

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.github.therajanmaurya.sweeterror.SweetUIErrorHandler
import com.mifos.mifosxdroid.R
import com.mifos.mifosxdroid.adapters.ClientNameListAdapter
import com.mifos.mifosxdroid.core.EndlessRecyclerViewScrollListener
import com.mifos.mifosxdroid.core.MifosBaseActivity
import com.mifos.mifosxdroid.core.MifosBaseFragment
import com.mifos.mifosxdroid.core.RecyclerItemClickListener
import com.mifos.mifosxdroid.core.util.Toaster
import com.mifos.mifosxdroid.dialogfragments.syncclientsdialog.SyncClientsDialogFragment
import com.mifos.mifosxdroid.online.ClientActivity
import com.mifos.mifosxdroid.online.createnewclient.CreateNewClientFragment
import com.mifos.mifosxdroid.online.createnewclient.CreateNewClientPresenter
import com.mifos.objects.client.Client
import com.mifos.objects.templates.clients.OfficeOptions
import com.mifos.utils.Constants
import com.mifos.utils.FragmentConstants
import com.mifos.utils.PrefManager.getBoolean
import java.util.*
import javax.inject.Inject

/**
 * Created by ishankhanna on 09/02/14.
 *
 *
 * This class loading client, Here is two way to load the clients. First one to load clients
 * from Rest API
 *
 *
 * >demo.openmf.org/fineract-provider/api/v1/clients?paged=true&offset=offset_value&limit
 * =limit_value>
 *
 *
 * Offset : From Where index, client will be fetch.
 * limit : Total number of client, need to fetch
 *
 *
 * and showing in the ClientList.
 *
 *
 * and Second one is showing Group Clients. Here Group load the ClientList and send the
 * Client to ClientListFragment newInstance(List<Client> clientList,
 * boolean isParentFragment) {...}
 * and unregister the ScrollListener and SwipeLayout.
</Client> */
class ClientListFragment : MifosBaseFragment(), RecyclerItemClickListener.OnItemClickListener, ClientListMvpView, OnItemSelectedListener, OnRefreshListener, Filterable {
    @kotlin.jvm.JvmField
    @BindView(R.id.rv_clients)
    var rv_clients: RecyclerView? = null

    @kotlin.jvm.JvmField
    @BindView(R.id.swipe_container)
    var swipeRefreshLayout: SwipeRefreshLayout? = null

    @kotlin.jvm.JvmField
    @BindView(R.id.layout_error)
    var errorView: View? = null

    @kotlin.jvm.JvmField
    @BindView(R.id.pb_client)
    var pb_client: ProgressBar? = null

    @kotlin.jvm.JvmField
    @BindView(R.id.sp_search)
    var sp_search: Spinner? = null

    @JvmField
    @Inject
    var mClientNameListAdapter: ClientNameListAdapter? = null

    @JvmField
    @Inject
    var mClientListPresenter: ClientListPresenter? = null

    @JvmField
    @Inject
    var createNewClientPresenter: CreateNewClientPresenter? = null
    private var rootView: View? = null
    private var clientList: MutableList<Client>? = null
    private var selectedClients: MutableList<Client>? = null
    private var actionModeCallback: ActionModeCallback? = null
    private var actionMode: ActionMode? = null
    private var isParentFragment = false
    private var mLayoutManager: LinearLayoutManager? = null
    private var clickedPosition = -1
    private var sweetUIErrorHandler: SweetUIErrorHandler? = null
    private var clientsListFilter: List<Client>? = null
    private var clientsFullList: List<Client>? = null
    private var officeList: MutableList<String>? = null
    private var isSearchFilter = false
    override fun onItemClick(childView: View, position: Int) {
        if (actionMode != null) {
            toggleSelection(position)
        } else {
            val clientActivityIntent = Intent(activity, ClientActivity::class.java)
            clientActivityIntent.putExtra(Constants.CLIENT_ID, clientList!![position].id)
            startActivity(clientActivityIntent)
            clickedPosition = position
        }
    }

    override fun onItemLongPress(childView: View, position: Int) {
        if (actionMode == null) {
            actionMode = (activity as MifosBaseActivity?)!!.startSupportActionMode(actionModeCallback!!)
        }
        toggleSelection(position)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clientList = ArrayList()
        officeList = ArrayList()
        selectedClients = ArrayList()
        clientsListFilter = ArrayList()
        actionModeCallback = ActionModeCallback()
        if (arguments != null) {
            clientList = arguments!!.getParcelableArrayList(Constants.CLIENTS)
            isParentFragment = arguments!!
                    .getBoolean(Constants.IS_A_PARENT_FRAGMENT)
        }
        setHasOptionsMenu(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.mItem_search) {
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_client, container, false)
        (activity as MifosBaseActivity?)!!.activityComponent.inject(this)
        setToolbarTitle(resources.getString(R.string.clients))
        ButterKnife.bind(this, rootView!!)
        mClientListPresenter!!.attachView(this)

        //setting all the UI content to the view
        showUserInterface()
        /**
         * This is the LoadMore of the RecyclerView. It called When Last Element of RecyclerView
         * is shown on the Screen.
         */
        rv_clients!!.addOnScrollListener(object : EndlessRecyclerViewScrollListener(mLayoutManager) {
            override fun onLoadMore(page: Int, totalItemCount: Int) {
                if (totalItemCount > 8) {
                    mClientListPresenter!!.loadClients(true, totalItemCount)
                }
            }
        })
        /**
         * First Check the Parent Fragment is true or false. If parent fragment is true then no
         * need to fetch clientList from Rest API, just need to showing parent fragment ClientList
         * and is Parent Fragment is false then Presenter make the call to Rest API and fetch the
         * Client Lis to show. and Presenter make transaction to Database to load saved clients.
         */
        if (isParentFragment) {
            mClientListPresenter!!.showParentClients(clientList!!)
        } else {
            mClientListPresenter!!.loadClients(false, 0)
        }
        mClientListPresenter!!.loadDatabaseClients()
        return rootView
    }

    override fun onResume() {
        super.onResume()
        if (clickedPosition != -1) {
            mClientNameListAdapter!!.updateItem(clickedPosition)
        }
    }

    /**
     * This method initializes the all Views.
     */
    override fun showUserInterface() {
        mLayoutManager = LinearLayoutManager(activity)
        mLayoutManager!!.orientation = LinearLayoutManager.VERTICAL
        mClientNameListAdapter!!.setContext(activity)
        rv_clients!!.layoutManager = mLayoutManager
        rv_clients!!.addOnItemTouchListener(RecyclerItemClickListener(activity, this))
        rv_clients!!.addOnScrollListener(object : EndlessRecyclerViewScrollListener(mLayoutManager) {
            override fun onLoadMore(page: Int, totalItemCount: Int) {
                if (totalItemCount > 8) {
                    mClientListPresenter!!.loadClients(true, totalItemCount)
                }
            }
        })
        rv_clients!!.setHasFixedSize(true)
        rv_clients!!.adapter = mClientNameListAdapter
        swipeRefreshLayout!!.setColorSchemeColors(*activity
                !!.resources.getIntArray(R.array.swipeRefreshColors))
        swipeRefreshLayout!!.setOnRefreshListener(this)
        sweetUIErrorHandler = SweetUIErrorHandler(activity, rootView)
    }

    @OnClick(R.id.fab_create_client)
    fun onClickCreateNewClient() {
        (activity as MifosBaseActivity?)!!.replaceFragment(CreateNewClientFragment.newInstance(),
                true, R.id.container)
    }

    /**
     * This method will be called when user will swipe down to Refresh the ClientList then
     * Presenter make the Fresh call to Rest API to load ClientList from offset = 0 and fetch the
     * first 100 clients and update the client list.
     */
    override fun onRefresh() {
        showUserInterface()
        mClientListPresenter!!.loadClients(false, 0)
        mClientListPresenter!!.loadDatabaseClients()
        if (actionMode != null) actionMode!!.finish()
    }

    /**
     * This Method unregister the RecyclerView OnScrollListener and SwipeRefreshLayout
     * and NoClientIcon click event.
     */
    override fun unregisterSwipeAndScrollListener() {
        rv_clients!!.clearOnScrollListeners()
        swipeRefreshLayout!!.isEnabled = false
    }

    /**
     * This Method showing the Simple Taster Message to user.
     *
     * @param message String Message to show.
     */
    override fun showMessage(message: Int) {
        Toaster.show(rootView, getStringMessage(message))
    }

    /**
     * Onclick Send Fresh Request for Client list.
     */
    @OnClick(R.id.btn_try_again)
    fun reloadOnError() {
        sweetUIErrorHandler!!.hideSweetErrorLayoutUI(rv_clients, errorView)
        mClientListPresenter!!.loadClients(false, 0)
        mClientListPresenter!!.loadDatabaseClients()
    }

    /**
     * Setting ClientList to the Adapter and updating the Adapter.
     */
    override fun showClientList(clients: List<Client>?) {
        if (isSearchFilter) {
            clientList = clients as MutableList<Client>?
            mClientNameListAdapter!!.setClients(clients)
            clientsFullList = ArrayList(clients)
            // mClientNameListAdapter.notifyDataSetChanged();
        } else {
            clientList = clients as MutableList<Client>?
            mClientNameListAdapter!!.setClients(clients)
            clientsFullList = ArrayList(clients)
            mClientNameListAdapter!!.notifyDataSetChanged()
        }
    }

    override fun showClientListLoadMore(clients: List<Client>?) {
        clientList = clients as MutableList<Client>?
        mClientNameListAdapter!!.setClients(clients)
        clientsFullList = ArrayList(clients)
        mClientNameListAdapter!!.notifyDataSetChanged()
    }

    /**
     * Updating Adapter Attached ClientList
     *
     * @param clients List<Client></Client>>
     */
    override fun showLoadMoreClients(clients: List<Client>?) {
        clients?.let { clientList!!.addAll(it) }
        // mClientNameListAdapter.notifyDataSetChanged();
    }

    /**
     * Initialize the contents of the Fragment host's standard options menu.  You
     * should place your menu items in to <var>menu</var>.  For this method
     * to be called, you must have first called [.setHasOptionsMenu].  See
     * for more information.
     *
     * @param menu     The options menu in which you place your items.
     * @param inflater
     * @see .setHasOptionsMenu
     *
     * @see .onPrepareOptionsMenu
     *
     * @see .onOptionsItemSelected
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val Item = menu.findItem(R.id.mItem_menu_search)
        Item.isVisible = true
        val searchView = Item.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                getFilter().filter(newText)
                return false
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }

    /**
     * Showing Fetched ClientList size is 0 and show there is no client to show.
     *
     * @param message String Message to show user.
     */
    override fun showEmptyClientList(message: Int) {
        sweetUIErrorHandler!!.showSweetEmptyUI(getString(R.string.client),
                getString(message), R.drawable.ic_error_black_24dp, rv_clients, errorView)
    }

    /**
     * This Method Will be called. When Presenter failed to First page of ClientList from Rest API.
     * Then user look the Message that failed to fetch clientList.
     */
    override fun showError() {
        val errorMessage = getStringMessage(R.string.failed_to_load_client)
        sweetUIErrorHandler!!.showSweetErrorUI(errorMessage, R.drawable.ic_error_black_24dp,
                rv_clients, errorView)
    }

    private var officeNameIdHashMap = HashMap<String, Int>()
    private val officeNames: List<String?> = ArrayList()

    override fun showOffices(offices: List<OfficeOptions>) {
        officeList!!.clear()
        officeNameIdHashMap = mClientListPresenter!!.createOfficeNameIdMap(offices, officeNames as MutableList<String?>)
        setSpinner(sp_search, officeNames)
        sp_search!!.onItemSelectedListener = this
    }

    private fun setSpinner(spinner: Spinner?, values: List<String?>) {
        val adapter = ArrayAdapter(activity,
                android.R.layout.simple_spinner_item, values)
        adapter.notifyDataSetChanged()
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner!!.adapter = adapter
    }

    override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
        val officeId: Int? = officeNameIdHashMap.get(officeNames[i])
        if (officeId != -1) {
            mClientListPresenter!!.loadClientsByOfficeId(false, 0, officeId)
            //  mClientNameListAdapter.notifyDataSetChanged();
        } else {
            Toaster.show(rootView, getString(R.string.error_select_office))
        }
    }

    override fun onNothingSelected(adapterView: AdapterView<*>?) {}
    fun getSelectedOffice(v: View?) {
        val officeOptions = sp_search!!.selectedItem as OfficeOptions
        fetchOfficeData(officeOptions)
    }

    private fun fetchOfficeData(officeOptions: OfficeOptions): Int {
        return officeOptions.id
    }

    /**
     * show MifosBaseActivity ProgressBar, if mClientNameListAdapter.getItemCount() == 0
     * otherwise show SwipeRefreshLayout.
     */
    override fun showProgressbar(show: Boolean) {
        swipeRefreshLayout!!.isRefreshing = show
        if (show && mClientNameListAdapter!!.itemCount == 0) {
            pb_client!!.visibility = View.VISIBLE
            swipeRefreshLayout!!.isRefreshing = false
        } else {
            pb_client!!.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideMifosProgressBar()
        mClientListPresenter!!.detachView()
        //As the Fragment Detach Finish the ActionMode
        if (actionMode != null) actionMode!!.finish()
    }

    /**
     * Toggle the selection state of an item.
     *
     *
     * If the item was the last one in the selection and is unselected, the selection is stopped.
     * Note that the selection must already be started (actionMode must not be null).
     *
     * @param position Position of the item to toggle the selection state
     */
    private fun toggleSelection(position: Int) {
        mClientNameListAdapter!!.toggleSelection(position)
        val count = mClientNameListAdapter!!.selectedItemCount
        if (count == 0) {
            actionMode!!.finish()
        } else {
            actionMode!!.title = count.toString()
            actionMode!!.invalidate()
        }
    }

    override fun getFilter(): Filter {
        return filter
    }

    private val filter: Filter = object : Filter() {
        override fun performFiltering(charSequence: CharSequence): FilterResults {
            val filteredList: MutableList<Client> = ArrayList()
            if (charSequence.toString().isEmpty()) {
                filteredList.addAll(clientsFullList!!)
            } else {
                val filterPattern = charSequence.toString().toLowerCase().trim { it <= ' ' }
                for (client in clientsFullList!!) {
                    if (client.displayName.toLowerCase().contains(filterPattern)) {
                        filteredList.add(client)
                    }
                    if (client.accountNo.contains(filterPattern)) {
                        filteredList.add(client)
                    }
                }
            }
            val filterResults = FilterResults()
            filterResults.values = filteredList
            return filterResults
        }

        override fun publishResults(charSequence: CharSequence, filterResults: FilterResults) {
            isSearchFilter = true
            clientList!!.clear()
            clientList!!.addAll(filterResults.values as List<Client>)
            mClientNameListAdapter!!.notifyDataSetChanged()
        }
    }

    /**
     * This ActionModeCallBack Class handling the User Event after the Selection of Clients. Like
     * Click of Menu Sync Button and finish the ActionMode
     */
    private inner class ActionModeCallback : ActionMode.Callback {
        private val LOG_TAG = ActionModeCallback::class.java.simpleName
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_sync, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_sync -> {
                    selectedClients!!.clear()
                    for (position in mClientNameListAdapter!!.selectedItems) {
                        selectedClients!!.add(clientList!![position!!])
                    }
                    val syncClientsDialogFragment = SyncClientsDialogFragment.newInstance(selectedClients)
                    val fragmentTransaction = activity
                            ?.supportFragmentManager?.beginTransaction()
                    fragmentTransaction?.addToBackStack(FragmentConstants.FRAG_CLIENT_SYNC)
                    syncClientsDialogFragment.isCancelable = false
                    fragmentTransaction?.let {
                        syncClientsDialogFragment.show(it,
                                resources.getString(R.string.sync_clients))
                    }
                    mode.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            mClientNameListAdapter!!.clearSelection()
            actionMode = null
        }
    }

    companion object {
        val LOG_TAG = ClientListFragment::class.java.simpleName
        /**
         * This Method will be called, whenever Parent (Fragment or Activity) will be true and Presenter
         * do not need to make Rest API call to server. Parent (Fragment or Activity) already fetched
         * the clients and for showing, they call ClientListFragment.
         *
         *
         * Example : Showing Group Clients.
         *
         * @param clientList       List<Client>
         * @param isParentFragment true
         * @return ClientListFragment
        </Client> */
        @JvmStatic
        fun newInstance(clientList: List<Client?>?,
                        isParentFragment: Boolean): ClientListFragment {
            val clientListFragment = ClientListFragment()
            val args = Bundle()
            if (isParentFragment && clientList != null) {
                args.putParcelableArrayList(Constants.CLIENTS,
                        clientList as ArrayList<out Parcelable?>?)
                args.putBoolean(Constants.IS_A_PARENT_FRAGMENT, true)
                clientListFragment.arguments = args
            }
            return clientListFragment
        }

        /**
         * This method will be called, whenever ClientListFragment will not have Parent Fragment.
         * So, Presenter make the call to Rest API and fetch the Client List and show in UI
         *
         * @return ClientListFragment
         */
        @JvmStatic
        fun newInstance(): ClientListFragment {
            val arguments = Bundle()
            val clientListFragment = ClientListFragment()
            clientListFragment.arguments = arguments
            return clientListFragment
        }

    }
}