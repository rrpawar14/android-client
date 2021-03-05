/*
 * This project is licensed under the open source MPL V2.
 * See https://github.com/openMF/android-client/blob/master/LICENSE.md
 */

package com.mifos.mifosxdroid.online.clientlist;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.FragmentTransaction;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.github.therajanmaurya.sweeterror.SweetUIErrorHandler;
import com.mifos.mifosxdroid.R;
import com.mifos.mifosxdroid.adapters.ClientNameListAdapter;
import com.mifos.mifosxdroid.core.EndlessRecyclerViewScrollListener;
import com.mifos.mifosxdroid.core.MifosBaseActivity;
import com.mifos.mifosxdroid.core.MifosBaseFragment;
import com.mifos.mifosxdroid.core.RecyclerItemClickListener;
import com.mifos.mifosxdroid.core.RecyclerItemClickListener.OnItemClickListener;
import com.mifos.mifosxdroid.core.util.Toaster;
import com.mifos.mifosxdroid.dialogfragments.syncclientsdialog.SyncClientsDialogFragment;
import com.mifos.mifosxdroid.online.ClientActivity;
import com.mifos.mifosxdroid.online.createnewclient.CreateNewClientFragment;
import com.mifos.mifosxdroid.online.createnewclient.CreateNewClientPresenter;
import com.mifos.mifosxdroid.online.generatecollectionsheet.GenerateCollectionSheetPresenter;
import com.mifos.objects.client.Client;
import com.mifos.objects.organisation.Office;
import com.mifos.objects.templates.clients.OfficeOptions;
import com.mifos.utils.Constants;
import com.mifos.utils.FragmentConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.function.ToIntFunction;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;


/**
 * Created by ishankhanna on 09/02/14.
 * <p>
 * This class loading client, Here is two way to load the clients. First one to load clients
 * from Rest API
 * <p>
 * </>demo.openmf.org/fineract-provider/api/v1/clients?paged=true&offset=offset_value&limit
 * =limit_value</>
 * <p>
 * Offset : From Where index, client will be fetch.
 * limit : Total number of client, need to fetch
 * <p>
 * and showing in the ClientList.
 * <p>
 * and Second one is showing Group Clients. Here Group load the ClientList and send the
 * Client to ClientListFragment newInstance(List<Client> clientList,
 * boolean isParentFragment) {...}
 * and unregister the ScrollListener and SwipeLayout.
 */
public class ClientListFragment extends MifosBaseFragment
        implements OnItemClickListener, ClientListMvpView, Spinner.OnItemSelectedListener, SwipeRefreshLayout.OnRefreshListener, Filterable {

    public static final String LOG_TAG = ClientListFragment.class.getSimpleName();

    @BindView(R.id.rv_clients)
    RecyclerView rv_clients;

    @BindView(R.id.swipe_container)
    SwipeRefreshLayout swipeRefreshLayout;

    @BindView(R.id.layout_error)
    View errorView;

    @BindView(R.id.pb_client)
    ProgressBar pb_client;

    @BindView(R.id.sp_search)
    Spinner sp_search;

    @Inject
    ClientNameListAdapter mClientNameListAdapter;

    @Inject
    ClientListPresenter mClientListPresenter;

    @Inject
    CreateNewClientPresenter createNewClientPresenter;


    private View rootView;
    private List<Client> clientList;
    private List<Client> selectedClients;
    private ActionModeCallback actionModeCallback;
    private ActionMode actionMode;
    private Boolean isParentFragment = false;
    private LinearLayoutManager mLayoutManager;
    private Integer clickedPosition = -1;
    private SweetUIErrorHandler sweetUIErrorHandler;
    private List<Client> clientsListFilter;
    private List<Client> clientsFullList;
    private List<String> officeList;
    private Boolean isSearchFilter = false;

    @Override
    public void onItemClick(View childView, int position) {
        if (actionMode != null) {
            toggleSelection(position);
        } else {
            Intent clientActivityIntent = new Intent(getActivity(), ClientActivity.class);
            clientActivityIntent.putExtra(Constants.CLIENT_ID, clientList.get(position).getId());
            startActivity(clientActivityIntent);
            clickedPosition = position;
        }
    }

    @Override
    public void onItemLongPress(View childView, int position) {
        if (actionMode == null) {
            actionMode = ((MifosBaseActivity) getActivity()).startSupportActionMode
                    (actionModeCallback);
        }
        toggleSelection(position);
    }

    /**
     * This method will be called, whenever ClientListFragment will not have Parent Fragment.
     * So, Presenter make the call to Rest API and fetch the Client List and show in UI
     *
     * @return ClientListFragment
     */
    public static ClientListFragment newInstance() {
        Bundle arguments = new Bundle();
        ClientListFragment clientListFragment = new ClientListFragment();
        clientListFragment.setArguments(arguments);
        return clientListFragment;
    }

    /**
     * This Method will be called, whenever Parent (Fragment or Activity) will be true and Presenter
     * do not need to make Rest API call to server. Parent (Fragment or Activity) already fetched
     * the clients and for showing, they call ClientListFragment.
     * <p>
     * Example : Showing Group Clients.
     *
     * @param clientList       List<Client>
     * @param isParentFragment true
     * @return ClientListFragment
     */
    public static ClientListFragment newInstance(List<Client> clientList,
                                                 boolean isParentFragment) {
        ClientListFragment clientListFragment = new ClientListFragment();
        Bundle args = new Bundle();
        if (isParentFragment && clientList != null) {
            args.putParcelableArrayList(Constants.CLIENTS,
                    (ArrayList<? extends Parcelable>) clientList);
            args.putBoolean(Constants.IS_A_PARENT_FRAGMENT, true);
            clientListFragment.setArguments(args);
        }
        return clientListFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        clientList = new ArrayList<>();
        officeList = new ArrayList<>();
        this.selectedClients = new ArrayList<>();
        this.clientsListFilter = new ArrayList<>();
        actionModeCallback = new ActionModeCallback();
        if (getArguments() != null) {
            clientList = getArguments().getParcelableArrayList(Constants.CLIENTS);
            isParentFragment = getArguments()
                    .getBoolean(Constants.IS_A_PARENT_FRAGMENT);
        }
        setHasOptionsMenu(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if(id == R.id.mItem_search){
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle
            savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_client, container, false);
        ((MifosBaseActivity) getActivity()).getActivityComponent().inject(this);
        setToolbarTitle(getResources().getString(R.string.clients));
        ButterKnife.bind(this, rootView);
        mClientListPresenter.attachView(this);

        //setting all the UI content to the view
        showUserInterface();

        /**
         * This is the LoadMore of the RecyclerView. It called When Last Element of RecyclerView
         * is shown on the Screen.
         */
        rv_clients.addOnScrollListener(new EndlessRecyclerViewScrollListener(mLayoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemCount) {
                mClientListPresenter.loadClients(true, totalItemCount);
            }
        });

        /**
         * First Check the Parent Fragment is true or false. If parent fragment is true then no
         * need to fetch clientList from Rest API, just need to showing parent fragment ClientList
         * and is Parent Fragment is false then Presenter make the call to Rest API and fetch the
         * Client Lis to show. and Presenter make transaction to Database to load saved clients.
         */
        if (isParentFragment) {
            mClientListPresenter.showParentClients(clientList);
        } else {
            mClientListPresenter.loadClients(false, 0);
        }
        mClientListPresenter.loadDatabaseClients();

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (clickedPosition != -1) {
            mClientNameListAdapter.updateItem(clickedPosition);
        }
    }

    /**
     * This method initializes the all Views.
     */
    @Override
    public void showUserInterface() {
        mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mClientNameListAdapter.setContext(getActivity());
        rv_clients.setLayoutManager(mLayoutManager);
        rv_clients.addOnItemTouchListener(new RecyclerItemClickListener(getActivity(), this));
        rv_clients.setHasFixedSize(true);
        rv_clients.setAdapter(mClientNameListAdapter);
        swipeRefreshLayout.setColorSchemeColors(getActivity()
                .getResources().getIntArray(R.array.swipeRefreshColors));
        swipeRefreshLayout.setOnRefreshListener(this);
        sweetUIErrorHandler = new SweetUIErrorHandler(getActivity(), rootView);
    }

    @OnClick(R.id.fab_create_client)
    void onClickCreateNewClient() {
        ((MifosBaseActivity) getActivity()).replaceFragment(CreateNewClientFragment.newInstance(),
                true, R.id.container);
    }

    /**
     * This method will be called when user will swipe down to Refresh the ClientList then
     * Presenter make the Fresh call to Rest API to load ClientList from offset = 0 and fetch the
     * first 100 clients and update the client list.
     */
    @Override
    public void onRefresh() {
        showUserInterface();
        mClientListPresenter.loadClients(false, 0);
        mClientListPresenter.loadDatabaseClients();
        if (actionMode != null) actionMode.finish();
    }

    /**
     * This Method unregister the RecyclerView OnScrollListener and SwipeRefreshLayout
     * and NoClientIcon click event.
     */
    @Override
    public void unregisterSwipeAndScrollListener() {
        rv_clients.clearOnScrollListeners();
        swipeRefreshLayout.setEnabled(false);
    }

    /**
     * This Method showing the Simple Taster Message to user.
     *
     * @param message String Message to show.
     */
    @Override
    public void showMessage(int message) {
        Toaster.show(rootView, getStringMessage(message));
    }

    /**
     * Onclick Send Fresh Request for Client list.
     */
    @OnClick(R.id.btn_try_again)
    public void reloadOnError() {
        sweetUIErrorHandler.hideSweetErrorLayoutUI(rv_clients, errorView);
        mClientListPresenter.loadClients(false, 0);
        mClientListPresenter.loadDatabaseClients();
    }

    /**
     * Setting ClientList to the Adapter and updating the Adapter.
     */

    @Override
    public void showClientList(List<Client> clients) {

        if(isSearchFilter){
            clientList = clients;
            mClientNameListAdapter.setClients(clients);
            this.clientsFullList = new ArrayList<>(clients);
            // mClientNameListAdapter.notifyDataSetChanged();
        }
        else{
            clientList = clients;
            mClientNameListAdapter.setClients(clients);
            this.clientsFullList = new ArrayList<>(clients);
            mClientNameListAdapter.notifyDataSetChanged();
        }

    }

    /**
     * Updating Adapter Attached ClientList
     *
     * @param clients List<Client></>
     */
    @Override
    public void showLoadMoreClients(List<Client> clients) {
        clientList.addAll(clients);
        mClientNameListAdapter.notifyDataSetChanged();
    }

    /**
     * Initialize the contents of the Fragment host's standard options menu.  You
     * should place your menu items in to <var>menu</var>.  For this method
     * to be called, you must have first called {@link #setHasOptionsMenu}.  See
     * for more information.
     *
     * @param menu     The options menu in which you place your items.
     * @param inflater
     * @see #setHasOptionsMenu
     * @see #onPrepareOptionsMenu
     * @see #onOptionsItemSelected
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        MenuItem Item = menu.findItem(R.id.mItem_menu_search);
        Item.setVisible(true);
        SearchView searchView = (SearchView) Item.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                 getFilter().filter(newText);
                return false;
            };
        });
        super.onCreateOptionsMenu(menu, inflater);
    };

    /**
     * Showing Fetched ClientList size is 0 and show there is no client to show.
     *
     * @param message String Message to show user.
     */
    @Override
    public void showEmptyClientList(int message) {
        sweetUIErrorHandler.showSweetEmptyUI(getString(R.string.client),
                getString(message), R.drawable.ic_error_black_24dp, rv_clients, errorView);
    }

    /**
     * This Method Will be called. When Presenter failed to First page of ClientList from Rest API.
     * Then user look the Message that failed to fetch clientList.
     */
    @Override
    public void showError() {
        String errorMessage = getStringMessage(R.string.failed_to_load_client);
        sweetUIErrorHandler.showSweetErrorUI(errorMessage, R.drawable.ic_error_black_24dp,
                rv_clients, errorView);
    }
    private HashMap<String, Integer> officeNameIdHashMap = new HashMap<String, Integer>();
    private List<String> officeNames = new ArrayList<String>();

    @Override
    public void showOffices(List<OfficeOptions> offices) {
        officeList.clear();
        officeNameIdHashMap = mClientListPresenter.createOfficeNameIdMap(offices, officeNames);
        setSpinner(sp_search, officeNames);
        sp_search.setOnItemSelectedListener(this);
    }

    private void setSpinner(Spinner spinner, List<String> values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_spinner_item, values);
        adapter.notifyDataSetChanged();
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }


    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        int officeId = officeNameIdHashMap.get(officeNames.get(i));
        if (officeId != -1) {
            mClientListPresenter.loadClientsByOfficeId(false, 0, officeId);
            //  mClientNameListAdapter.notifyDataSetChanged();
        } else {
            Toaster.show(rootView, getString(R.string.error_select_office));
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    public void getSelectedOffice(View v){

        OfficeOptions officeOptions = (OfficeOptions) sp_search.getSelectedItem();
        fetchOfficeData(officeOptions);
    }

    private int fetchOfficeData(OfficeOptions officeOptions){
        int id = officeOptions.getId();
        return id;
    }

    /**
     * show MifosBaseActivity ProgressBar, if mClientNameListAdapter.getItemCount() == 0
     * otherwise show SwipeRefreshLayout.
     */
    @Override
    public void showProgressbar(boolean show) {
        swipeRefreshLayout.setRefreshing(show);
        if (show && mClientNameListAdapter.getItemCount() == 0) {
            pb_client.setVisibility(VISIBLE);
            swipeRefreshLayout.setRefreshing(false);
        } else {
            pb_client.setVisibility(GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        hideMifosProgressBar();
        mClientListPresenter.detachView();
        //As the Fragment Detach Finish the ActionMode
        if (actionMode != null) actionMode.finish();
    }

    /**
     * Toggle the selection state of an item.
     * <p>
     * If the item was the last one in the selection and is unselected, the selection is stopped.
     * Note that the selection must already be started (actionMode must not be null).
     *
     * @param position Position of the item to toggle the selection state
     */
    private void toggleSelection(int position) {
        mClientNameListAdapter.toggleSelection(position);
        int count = mClientNameListAdapter.getSelectedItemCount();

        if (count == 0) {
            actionMode.finish();
        } else {
            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

    final Filter filter = new Filter() {

        @Override
        protected FilterResults performFiltering(CharSequence charSequence) {
            List<Client> filteredList = new ArrayList<>();
            if (charSequence.toString().isEmpty()) {
                filteredList.addAll(clientsFullList);
            } else {
                String filterPattern = charSequence.toString().toLowerCase().trim();

                for(Client client: clientsFullList){
                    if(client.getDisplayName().toLowerCase().contains(filterPattern)){
                        filteredList.add(client);
                    }
                    if(client.getAccountNo().contains(filterPattern)){
                        filteredList.add(client);
                    }
                }
            }
            FilterResults filterResults = new FilterResults();
            filterResults.values = filteredList;
            return filterResults;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            isSearchFilter = true;
            clientList.clear();
            clientList.addAll((List) filterResults.values);
            mClientNameListAdapter.notifyDataSetChanged();
        }
    };

    /**
     * This ActionModeCallBack Class handling the User Event after the Selection of Clients. Like
     * Click of Menu Sync Button and finish the ActionMode
     */
    private class ActionModeCallback implements ActionMode.Callback {
        @SuppressWarnings("unused")
        private final String LOG_TAG = ActionModeCallback.class.getSimpleName();

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_sync, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_sync:

                    selectedClients.clear();
                    for (Integer position : mClientNameListAdapter.getSelectedItems()) {
                        selectedClients.add(clientList.get(position));
                    }

                    SyncClientsDialogFragment syncClientsDialogFragment =
                            SyncClientsDialogFragment.newInstance(selectedClients);
                    FragmentTransaction fragmentTransaction = getActivity()
                            .getSupportFragmentManager().beginTransaction();
                    fragmentTransaction.addToBackStack(FragmentConstants.FRAG_CLIENT_SYNC);
                    syncClientsDialogFragment.setCancelable(false);
                    syncClientsDialogFragment.show(fragmentTransaction,
                            getResources().getString(R.string.sync_clients));
                    mode.finish();
                    return true;

                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mClientNameListAdapter.clearSelection();
            actionMode = null;
        }
    }
}