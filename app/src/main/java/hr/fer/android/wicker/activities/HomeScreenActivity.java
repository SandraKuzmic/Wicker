package hr.fer.android.wicker.activities;

import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.app.NotificationManagerCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hr.fer.android.wicker.R;
import hr.fer.android.wicker.WickerConstant;
import hr.fer.android.wicker.WickerUtils;
import hr.fer.android.wicker.adapters.HomeScreenListAdapter;
import hr.fer.android.wicker.db.CounterDatabase;
import hr.fer.android.wicker.entity.Counter;

public class HomeScreenActivity extends AppCompatActivity {

    private int numOfCounters;

    private ListAdapter dataAdapter;
    private ListView dataListView;

    private Menu menu;

    private boolean isSearch;
    private String query;

    private Toast mToast;

    private FloatingActionButton fabAddCounter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_screen);

        dataListView = findViewById(R.id.home_screen_list);
        setEmptyViewRandomText();

        fabAddCounter = findViewById(R.id.fabAddCounter);
        fabAddCounter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentNew = new Intent(HomeScreenActivity.this, MainActivity.class);
                startActivityForResult(intentNew, WickerConstant.REQUEST_CODE);
            }
        });

        updateDataListView();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            isSearch = true;
            query = intent.getExtras().getString(SearchManager.QUERY).toLowerCase().trim();
            hideItems();
        }
        if (Intent.ACTION_DEFAULT.equals(intent.getAction())) {
            isSearch = false;
            showItems();
        }
        updateDataListView();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            updateDataListView();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu_no_login, menu);

        this.menu = menu;

        SearchView searchView = (SearchView) menu.findItem(R.id.home_search).getActionView();

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                isSearch = true;
                query = newText;
                updateDataListView();
                return true;
            }
        });

        menu.findItem(R.id.home_search).setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                hideItems();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                Intent intent = new Intent(HomeScreenActivity.this, HomeScreenActivity.class);
                intent.setAction(Intent.ACTION_DEFAULT);
                intent.removeExtra(SEARCH_SERVICE);
                startActivity(intent);
                return true;
            }
        });

        return true;
    }

    private void showItems() {
        menu.setGroupVisible(R.id.home_group_in_search_shown, true);
        fabAddCounter.setVisibility(View.VISIBLE);
    }

    private void hideItems() {
        menu.setGroupVisible(R.id.home_group_in_search_shown, false);
        fabAddCounter.setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.home_order:
                orderBy();
                return true;
            case R.id.home_clear_all:
                clearAll();
                return true;
            case R.id.home_import:
                importAddCounter();
                return true;
            case R.id.home_settings:
                startActivity(new Intent(HomeScreenActivity.this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setEmptyViewRandomText() {
        TextView emptyTextView1 = findViewById(R.id.home_screen_tw_empty);
        String[] emptyText = {getString(R.string.random_welcome_text0_a) + '\n' + getString(R.string.random_welcome_text0_b),
                getString(R.string.random_welcome_text1),
                getString(R.string.random_welcome_text2),
                getString(R.string.random_welcome_text3)};
        Random rand = new Random();
        emptyTextView1.setText(emptyText[rand.nextInt(4)]);
        dataListView.setEmptyView(findViewById(R.id.home_screen_empty_view));
    }

    private void orderBy() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.order_by));
        final SharedPreferences pref = getSharedPreferences(WickerConstant.PREFS_ORDER, MODE_PRIVATE);
        builder.setSingleChoiceItems(R.array.spinner_order, pref.getInt(WickerConstant.ORDER, 0), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                SharedPreferences.Editor editor = pref.edit();
                editor.putInt(WickerConstant.ORDER, which);
                editor.apply();
                updateDataListView();
            }
        });
        builder.show();
    }

    private void clearAll() {
        if (numOfCounters == 0) {
            mToast = WickerUtils.addToast(mToast, HomeScreenActivity.this, R.string.no_counters_to_delete, true);
            return;
        }
        final AlertDialog.Builder deleteAlert = new AlertDialog.Builder(HomeScreenActivity.this);
        deleteAlert.setTitle(getString(R.string.delete));
        deleteAlert.setMessage(numOfCounters + " "
                + (numOfCounters == 1 ? getString(R.string.deleted_counter_single) : getString(R.string.deleted_counter_multiple)));
        deleteAlert.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new AsyncTask<String, Integer, Long>() {
                    @Override
                    protected Long doInBackground(String... params) {
                        CounterDatabase database = new CounterDatabase(HomeScreenActivity.this);
                        for (Counter tmp : database.getDatabaseCounterListData())
                            NotificationManagerCompat.from(HomeScreenActivity.this).cancel(tmp.getId().intValue());
                        database.deleteAllData();
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Long retValue) {
                        dataListView.setAdapter(null);
                        ActionBar actionBar = getSupportActionBar();
                        numOfCounters = 0;
                        actionBar.setTitle(getString(R.string.all_data) + " (0)");
                    }
                }.execute("Delete all");
            }
        });
        deleteAlert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //do nothing
                dialog.cancel();
            }
        });
        deleteAlert.show();

    }

    private void importAddCounter() {
        final AlertDialog.Builder builderImportCounter = new AlertDialog.Builder(HomeScreenActivity.this);
        builderImportCounter.setTitle(R.string.import_counter_alert);
        builderImportCounter.setMessage(R.string.exported_copy);

        final EditText inputImport = new EditText(HomeScreenActivity.this);
        inputImport.setInputType(InputType.TYPE_CLASS_TEXT);
        inputImport.setVerticalScrollBarEnabled(true);
        builderImportCounter.setView(inputImport);

        builderImportCounter.setPositiveButton(R.string.import_counter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String importText = inputImport.getText().toString();
                StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
                encryptor.setPassword(WickerConstant.ENCRYPTION_PASSWORD);
                Pattern pattern = Pattern.compile("(.+|\\p{Punct}),(\\w+\\.\\w+),(\\w+\\.\\w+),(\\w+),(\\w+),(.*)");
                try {
                    final Matcher matcher = pattern.matcher(encryptor.decrypt(importText));
                    if (matcher.find()) {
                        final Counter counter = new Counter(null,
                                matcher.group(1),
                                Double.parseDouble(matcher.group(2)),
                                Double.parseDouble(matcher.group(3)),
                                Long.parseLong(matcher.group(4)),
                                Long.parseLong(matcher.group(5)),
                                matcher.group(6));
                        new AsyncTask<String, Integer, Long>() {

                            @Override
                            protected Long doInBackground(String... params) {
                                CounterDatabase db = new CounterDatabase(HomeScreenActivity.this);
                                return db.addCounter(counter);
                            }

                            @Override
                            protected void onPostExecute(Long id) {
                                if (WickerConstant.ERROR_CODE_LONG.equals(id)) {
                                    importUpdateCounter(counter);
                                    return;
                                }
                                mToast = WickerUtils.addToast(mToast, HomeScreenActivity.this, R.string.imported, true);
                                updateDataListView();
                            }
                        }.execute();
                    } else {
                        mToast = WickerUtils.addToast(mToast, HomeScreenActivity.this, R.string.error, true);
                    }
                } catch (Exception e) {
                    mToast = WickerUtils.addToast(mToast, HomeScreenActivity.this, R.string.error, true);
                }
            }
        });
        builderImportCounter.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builderImportCounter.show();
    }

    private void importUpdateCounter(final Counter counter) {
        final AlertDialog.Builder builderImportCounter = new AlertDialog.Builder(HomeScreenActivity.this);
        builderImportCounter.setTitle(R.string.counter_exists);
        builderImportCounter.setMessage(R.string.update_existing);

        builderImportCounter.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new AsyncTask<String, Integer, Long>() {

                            @Override
                            protected Long doInBackground(String... params) {
                                CounterDatabase db = new CounterDatabase(HomeScreenActivity.this);
                                for (Counter tmp : db.getDatabaseCounterListData()) {
                                    if (tmp.getName().equals(counter.getName())) {
                                        counter.setId(tmp.getId());
                                        NotificationManagerCompat.from(HomeScreenActivity.this).cancel(tmp.getId().intValue());
                                        break;
                                    }
                                }
                                return db.updateCounter(counter);
                            }

                            @Override
                            protected void onPostExecute(Long id) {
                                if (WickerConstant.ERROR_CODE_LONG.equals(id)) {
                                    mToast = WickerUtils.addToast(mToast, HomeScreenActivity.this, R.string.error, true);
                                    return;
                                }
                                mToast = WickerUtils.addToast(mToast, HomeScreenActivity.this, R.string.imported, true);
                                updateDataListView();
                            }
                        }.execute();
                    }
                }
        );
        builderImportCounter.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builderImportCounter.show();
    }


    private class AsyncGetDataTask extends AsyncTask<String, Integer, List<Counter>> {
        @Override
        protected List<Counter> doInBackground(String... params) {
            CounterDatabase database = new CounterDatabase(HomeScreenActivity.this);
            return database.getDatabaseCounterListData();
        }

        @Override
        protected void onPostExecute(final List<Counter> data) {
            List<Counter> finalData = new LinkedList<>();
            if (isSearch) {
                for (Counter counter : data)
                    if (counter.getName().toLowerCase().contains(query))
                        finalData.add(counter);
            } else {
                finalData = data;
            }

            dataAdapter = new HomeScreenListAdapter(HomeScreenActivity.this, finalData);

            dataListView.setAdapter(dataAdapter);

            numOfCounters = finalData.size();
            ActionBar actionBar = getSupportActionBar();
            actionBar.setTitle(getString(R.string.all_data) + " (" + numOfCounters + ")");

            Collections.sort(finalData, new Comparator<Counter>() {
                @Override
                public int compare(Counter counter1, Counter counter2) {
                    switch (getSharedPreferences(WickerConstant.PREFS_ORDER, MODE_PRIVATE).getInt(WickerConstant.ORDER, 0)) {
                        case 0://desc
                            return -1 * counter1.getDateModified().compareTo(counter2.getDateModified());
                        case 1://desc
                            return -1 * counter1.getDateCreated().compareTo(counter2.getDateCreated());
                        case 2:
                            return counter1.getName().toLowerCase().compareTo(counter2.getName().toLowerCase());
                        case 3://desc
                            return Double.compare(counter2.getValue(), counter1.getValue());
                        default:
                            return -1 * counter1.getDateModified().compareTo(counter2.getDateModified());
                    }
                }
            });

            final List<Counter> sortedList = finalData;
            dataListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Counter counter = sortedList.get(position);
                    Intent intentLoad = new Intent(HomeScreenActivity.this, MainActivity.class);
                    intentLoad.putExtra(WickerConstant.COUNTER_BUNDLE_KEY, counter);
                    startActivityForResult(intentLoad, WickerConstant.REQUEST_CODE);
                }
            });

            dataListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    final Counter counter = sortedList.get(position);

                    final ArrayAdapter<String> optionAdapter = new ArrayAdapter<>(HomeScreenActivity.this, android.R.layout.simple_list_item_1);
                    optionAdapter.add(getString(R.string.share));
                    optionAdapter.add(getString(R.string.export_counter));
                    optionAdapter.add(getString(R.string.delete));

                    AlertDialog.Builder builder = new AlertDialog.Builder(HomeScreenActivity.this);
                    builder.setTitle(counter.getName());
                    builder.setAdapter(optionAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case 0:
                                    WickerUtils.shareCounter(HomeScreenActivity.this, counter, mToast);
                                    break;
                                case 1:
                                    mToast = WickerUtils.exportCounter(HomeScreenActivity.this, counter, mToast);
                                    break;
                                case 2:
                                    deleteCounter(counter);
                                    break;
                                default:
                                    break;
                            }
                        }
                    });
                    builder.show();
                    return true;
                }
            });
        }
    }

    private void deleteCounter(final Counter counter) {
        AlertDialog.Builder deleteAlert = new AlertDialog.Builder(HomeScreenActivity.this);
        deleteAlert.setTitle(getString(R.string.delete));
        deleteAlert.setMessage(getString(R.string.counter) + " " + counter.getName() + " " + getString(R.string.will_be_deleted));
        deleteAlert.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, int which) {
                new AsyncTask<String, List, Integer>() {

                    @Override
                    protected Integer doInBackground(String... params) {
                        CounterDatabase database = new CounterDatabase(HomeScreenActivity.this);
                        database.deleteCounter(counter);
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Integer integer) {
                        mToast = WickerUtils.addToast(mToast, HomeScreenActivity.this, R.string.success_delete, true);
                        NotificationManagerCompat.from(HomeScreenActivity.this).cancel(counter.getId().intValue());
                        updateDataListView();
                        dialog.cancel();
                    }
                }.execute();
            }
        });
        deleteAlert.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        deleteAlert.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == WickerConstant.REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                updateDataListView();
            }
        }
        setEmptyViewRandomText();
    }

    public void updateDataListView() {
        new AsyncGetDataTask().execute("update");
    }
}
