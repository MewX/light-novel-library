package org.mewx.wenku8.fragment;

import android.content.ContentValues;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.NovelInfoActivity;
import org.mewx.wenku8.adapter.NovelItemAdapterUpdate;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.NovelItemList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.util.LightNetwork;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;

public class NovelItemListFragment extends Fragment implements MyItemClickListener, MyItemLongClickListener {

    // type def
    private final String searchType = "search";
    private String type, key;
    private boolean isLoading = false; // judge network thread continue

    // members
    private ActionBar actionBar = null;
    private LinearLayoutManager mLayoutManager = null;
    private RecyclerView mRecyclerView = null;
    private SmoothProgressBar spb = null;

    // novel list info
    private List<Integer> listNovelItemAid = null; // aid list
    private List<NovelItemInfoUpdate> listNovelItemInfo = null; // novel info list
    private NovelItemAdapterUpdate mAdapter = null;

    // page info
    private int currentPage = 1; // default 1
    private int totalPage = 0; // default 0

    public static NovelItemListFragment newInstance(Bundle args) {
        NovelItemListFragment fragment = new NovelItemListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getArguments().getString("type");
        // judge if is 'search'
        key = type != null && type.equals(searchType) ? getArguments().getString("key") : "";

        actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_novel_item_list,container,false);
        rootView.setTag(type); // set TAG

        // init values
        listNovelItemAid = null;
        listNovelItemInfo = null;
        currentPage = 1; // default 1
        totalPage = 0; // default 0

        mLayoutManager = null;
        mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView = null;
        mRecyclerView = rootView.findViewById(R.id.novel_item_list);
        mRecyclerView.setHasFixedSize(false); // set variable size
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setLayoutManager(mLayoutManager);

        // List request
        if(type.equals(searchType)) {
            // update UI
            spb = getActivity().findViewById(R.id.spb);
            spb.progressiveStart();

            // excute task
            Toast.makeText(getActivity(),"search",Toast.LENGTH_SHORT).show();
            AsyncGetSearchResultList asyncGetSearchResultList = new AsyncGetSearchResultList();
            asyncGetSearchResultList.execute(key);
        }
        else {
            // Listener
            mRecyclerView.addOnScrollListener(new MyOnScrollListener());
            mRecyclerView.addOnScrollListener(new OnHidingScrollListener());
            AsyncGetNovelItemList asyncGetNovelItemList = new AsyncGetNovelItemList();
            asyncGetNovelItemList.execute(currentPage);
        }
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }


    @Override
    public void onItemClick(View view, final int position) {
        //Toast.makeText(getActivity(),"item click detected", Toast.LENGTH_SHORT).show();
        if(position < 0 || position >= listNovelItemAid.size()) {
            // ArrayIndexOutOfBoundsException
            Toast.makeText(getActivity(), "ArrayIndexOutOfBoundsException: " + position + " in size " + listNovelItemAid.size(), Toast.LENGTH_SHORT).show();
            return;
        }

        // go to detail activity
        Intent intent = new Intent(getActivity(), NovelInfoActivity.class);
        intent.putExtra("aid", listNovelItemAid.get(position));
        intent.putExtra("from", "list");
        intent.putExtra("title", ((TextView) view.findViewById(R.id.novel_title)).getText());

        if(Build.VERSION.SDK_INT < 21) {
            startActivity(intent);
        }
        else {
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                    Pair.create(view.findViewById(R.id.novel_cover), "novel_cover"),
                    Pair.create(view.findViewById(R.id.novel_title), "novel_title"));
            ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
        }
    }

    @Override
    public void onItemLongClick(View view, int position) {
        // empty
    }

    private class OnHidingScrollListener extends RecyclerView.OnScrollListener {
        int toolbarMarginOffset = 0;

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            toolbarMarginOffset += dy;
            if (toolbarMarginOffset > actionBar.getHeight())
                actionBar.hide();
            if (toolbarMarginOffset == 0)
                actionBar.show();
        }
    }


    /**
     * Refresh all the list with Integer array.
     * If empty, create;
     */
    private void refreshIdList() {
        if(listNovelItemAid==null)
            listNovelItemAid = new ArrayList<>();

        // set empty list with id only
        if(listNovelItemInfo == null)
            listNovelItemInfo = new ArrayList<>();
        else
            listNovelItemInfo.clear();

        // set empty
        for(Integer temp : listNovelItemAid) {
            listNovelItemInfo.add(new NovelItemInfoUpdate(temp));
        }

        //if(mAdapter == null) {
        if(mAdapter == null) {
            mAdapter = new NovelItemAdapterUpdate();
            mAdapter.setOnItemClickListener(this);
            mAdapter.setOnItemLongClickListener(this);
        }
        mAdapter.RefreshDataset(listNovelItemInfo);

        if(currentPage == 1 && mRecyclerView != null) {
            mRecyclerView.setAdapter(mAdapter);
        }
        else
            mAdapter.notifyDataSetChanged();
    }

    private void appendToIdList(List<Integer> l) {
        if(listNovelItemAid==null)
            listNovelItemAid = new ArrayList<>();
        if(l!=null)
            listNovelItemAid.addAll(l);
    }

    private class MyOnScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            int pastVisiblesItems, visibleItemCount, totalItemCount;
            visibleItemCount = mLayoutManager.getChildCount();
            totalItemCount = mLayoutManager.getItemCount();
            pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition();

            if (!isLoading) {
                // 滚动到一半的时候加载，即：剩余2个元素的时候就加载
                if (visibleItemCount + pastVisiblesItems + 2 >= totalItemCount && (totalPage==0 || currentPage < totalPage)) {
                    // load more toast
                    Snackbar.make(mRecyclerView, getResources().getString(R.string.list_loading)
                                    + "(" + Integer.toString(currentPage + 1) + "/" + Integer.toString(totalPage) + ")",
                            Snackbar.LENGTH_SHORT).show();

                    // load more thread
                    AsyncGetNovelItemList asynctask = new AsyncGetNovelItemList();
                    asynctask.execute(currentPage + 1);
                }
            }
        }
    }

    private class AsyncGetNovelItemList extends AsyncTask<Integer, Integer, Integer> {
        private List<Integer> tempNovelList = null;
        @Override
        protected Integer doInBackground(Integer... params) {
            if(isLoading)
                return -1;
            isLoading = true;
            currentPage = params[0];

            // params[0] is current page number
            ContentValues cv = Wenku8API.getNovelList(Wenku8API.getNOVELSORTBY(type), currentPage);
            byte[] temp = LightNetwork.LightHttpPostConnection( Wenku8API.BASE_URL, cv);
            if(temp == null) return -1;
            try {
                tempNovelList = Wenku8Parser.parseNovelItemList(new String(temp, "UTF-8"));
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            // judge result
            if( tempNovelList == null || tempNovelList.size() == 0 ) {
                String error = "in AsyncGetNovelItemList: doInBackground: tempNovelList == null || tempNovelList.size() == 0";
            }
            else {
                totalPage = tempNovelList.get(0);
                tempNovelList.remove(0);
            }

            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            if(integer == -1) {
                // network error
                return;
            }
            if(tempNovelList != null && tempNovelList.size()==0) {
                String error = "in AsyncGetNovelItemList: doInBackground: tempNovelList == null || tempNovelList.size() == 0";
                return;
            }

            // add to total list
            appendToIdList(tempNovelList);
            tempNovelList = null;

            refreshIdList();
            isLoading = false;
            super.onPostExecute(integer);
        }
    }

    private class AsyncGetSearchResultList extends AsyncTask<String, Integer, Integer> {

        @Override
        protected Integer doInBackground(String... params) {

            List<Integer> listResultList = new ArrayList<>(); // result list
            List<Integer> listResultList2 = new ArrayList<>(); // result list

            switch(GlobalConfig.searchType) {
                case web:
                    // get search result by novel title
                    ContentValues cv = Wenku8API.searchNovelByNovelName(params[0], GlobalConfig.getCurrentLang());
                    byte[] tempListTitle = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                    if(tempListTitle == null) return -1;

                    // purify returned data
                    try {
                        //Log.i("MewX", new String(tempListTitle, "UTF-8"));
                        Pattern p = Pattern.compile("aid=\'(.*)\'"); // match content between "aid=\'" and "\'"
                        Matcher m = p.matcher(new String(tempListTitle, "UTF-8"));
                        while (m.find())
                            listResultList.add(Integer.valueOf(m.group(1)));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }

                    // get search result by author name
                    cv = Wenku8API.searchNovelByAuthorName(params[0], GlobalConfig.getCurrentLang());
                    byte[] tempListName = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                    if(tempListName == null) return -1;

                    break;
                case bookshelf:
                    List<NovelItemInfoUpdate> listNovelItemInfo = new ArrayList<>();
                    for(int aid : GlobalConfig.getLocalBookshelfList()) {
                        String xml = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-intro.xml");
                        if (!xml.isEmpty()) {
                            NovelItemInfoUpdate niiu = NovelItemInfoUpdate.convertFromMeta(
                                    Objects.requireNonNull(Wenku8Parser.parseNovelFullMeta(xml)));
                            if(niiu != null) {
                                listNovelItemInfo.add(niiu);
                            }
                        }
                    }
                    for(NovelItemInfoUpdate info : listNovelItemInfo) {
                        if(info.author.toLowerCase().contains(params[0].toLowerCase()) ||
                                info.title.toLowerCase().contains(params[0].toLowerCase())) {
                            listResultList.add(info.aid);
                        }
                    }
                    break;
            }


            // set migrate
            listNovelItemAid = new ArrayList<>();
            listNovelItemAid.addAll(listResultList);
            listNovelItemAid.removeAll(listResultList2);
            listNovelItemAid.addAll(listResultList2);
            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);

            spb.progressiveStop();
            if(integer == -1) {
                Toast.makeText(getActivity(), getResources().getString(R.string.system_network_error),Toast.LENGTH_LONG).show();
                return;
            }
            if(listNovelItemAid == null || listNovelItemAid.size() == 0) {
                Toast.makeText(getActivity(), getResources().getString(R.string.task_null),Toast.LENGTH_LONG).show();
                return;
            }
            // show all items
            refreshIdList();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if(actionBar != null)
            actionBar.show();
    }
}
