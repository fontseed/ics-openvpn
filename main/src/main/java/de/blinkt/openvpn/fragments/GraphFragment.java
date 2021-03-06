/*
 * Copyright (c) 2012-2017 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.core.OpenVPNManagement;
import de.blinkt.openvpn.core.TrafficHistory;
import de.blinkt.openvpn.core.VpnStatus;

import static android.content.Context.MODE_PRIVATE;
import static de.blinkt.openvpn.core.OpenVPNService.humanReadableByteCount;
import static java.lang.Math.max;

/**
 * Created by arne on 19.05.17.
 */

public class GraphFragment extends Fragment implements VpnStatus.ByteCountListener {
    private static final String PREF_USE_LOG = "useLogGraph";
    private ListView mListView;

    private ChartDataAdapter mChartAdapter;
    private int mColorIn;
    private int mColorOut;

    private long firstTs;
    private TextView mSpeedStatus;
    private boolean mLogScale;

    private static final int TIME_PERIOD_SECDONS = 0;
    private static final int TIME_PERIOD_MINUTES = 1;
    private static final int TIME_PERIOD_HOURS = 2;
    private Handler mHandler;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.graph, container, false);
        mListView = (ListView) v.findViewById(R.id.graph_listview);
        mSpeedStatus = (TextView) v.findViewById(R.id.speedStatus);
        CheckBox logScaleView = (CheckBox) v.findViewById(R.id.useLogScale);
        mLogScale = getActivity().getPreferences(MODE_PRIVATE).getBoolean(PREF_USE_LOG, false);
        logScaleView.setChecked(mLogScale);

        List<Integer> charts = new LinkedList<>();
        charts.add(TIME_PERIOD_SECDONS);
        charts.add(TIME_PERIOD_MINUTES);
        charts.add(TIME_PERIOD_HOURS);

        mChartAdapter = new ChartDataAdapter(getActivity(), charts);
        mListView.setAdapter(mChartAdapter);

        mColorIn = getActivity().getResources().getColor(R.color.dataIn);
        mColorOut = getActivity().getResources().getColor(R.color.dataOut);


        logScaleView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mLogScale = isChecked;
                mChartAdapter.notifyDataSetChanged();
                getActivity().getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_USE_LOG, isChecked).apply();
            }
        });

        mHandler = new Handler();

        return v;


    }

    private Runnable triggerRefresh = new Runnable() {
        @Override
        public void run() {
            mChartAdapter.notifyDataSetChanged();
            mHandler.postDelayed(triggerRefresh, OpenVPNManagement.mBytecountInterval*1500);
        }
    };

    @Override
    public void onResume() {
        super.onResume();

        VpnStatus.addByteCountListener(this);
        mHandler.postDelayed(triggerRefresh, OpenVPNManagement.mBytecountInterval*1500);
    }

    @Override
    public void onPause() {
        super.onPause();

        mHandler.removeCallbacks(triggerRefresh);
        VpnStatus.removeByteCountListener(this);
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (firstTs == 0)
            firstTs = System.currentTimeMillis() / 100;

        long now = (System.currentTimeMillis() / 100) - firstTs;
        int interval = OpenVPNManagement.mBytecountInterval * 10;

        final String netstat = String.format(getString(R.string.statusline_bytecount),
                humanReadableByteCount(in, false),
                humanReadableByteCount(diffIn / OpenVPNManagement.mBytecountInterval, true),
                humanReadableByteCount(out, false),
                humanReadableByteCount(diffOut / OpenVPNManagement.mBytecountInterval, true));

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mHandler.removeCallbacks(triggerRefresh);
                mSpeedStatus.setText(netstat);
                mChartAdapter.notifyDataSetChanged();
                mHandler.postDelayed(triggerRefresh, OpenVPNManagement.mBytecountInterval*1500);
            }
        });

    }

    private class ChartDataAdapter extends ArrayAdapter<Integer> {

        private Context mContext;

        public ChartDataAdapter(Context context, List<Integer> trafficData) {
            super(context, 0, trafficData);
            mContext = context;
        }

        @NonNull
        @Override
        public View getView(final int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder = null;

            if (convertView == null) {

                holder = new ViewHolder();

                convertView = LayoutInflater.from(mContext).inflate(
                        R.layout.graph_item, parent, false);
                holder.chart = (LineChart) convertView.findViewById(R.id.chart);
                holder.title = (TextView) convertView.findViewById(R.id.tvName);
                convertView.setTag(holder);

            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            // apply styling
            // holder.chart.setValueTypeface(mTf);
            holder.chart.getDescription().setEnabled(false);
            holder.chart.setDrawGridBackground(false);

            XAxis xAxis = holder.chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);
            xAxis.setDrawAxisLine(true);

            switch (position){
                case TIME_PERIOD_HOURS:
                    holder.title.setText(R.string.avghour);
                    break;
                case TIME_PERIOD_MINUTES:
                    holder.title.setText(R.string.avgmin);
                    break;
                default:
                    holder.title.setText(R.string.last5minutes);
                    break;
            }

            xAxis.setValueFormatter(new IAxisValueFormatter() {


                @Override
                public String getFormattedValue(float value, AxisBase axis) {
                    switch (position){
                        case TIME_PERIOD_HOURS:
                            return String.format(Locale.getDefault(), "%.0f\u2009h ago", (axis.getAxisMaximum() - value) / 10/3600);
                        case TIME_PERIOD_MINUTES:
                            return String.format(Locale.getDefault(), "%.0f\u2009m ago", (axis.getAxisMaximum() - value) / 10/60);
                        default:
                            return String.format(Locale.getDefault(), "%.0f\u2009s ago", (axis.getAxisMaximum() - value) / 10);
                    }

                }
            });
            xAxis.setLabelCount(5);

            YAxis yAxis = holder.chart.getAxisLeft();
            yAxis.setLabelCount(5, false);

            yAxis.setValueFormatter(new IAxisValueFormatter() {
                @Override
                public String getFormattedValue(float value, AxisBase axis) {
                    if (mLogScale && value < 2.1f)
                        return "< 100\u2009bit";
                    if (mLogScale)
                        value = (float) Math.pow(10, value)/8;

                    return humanReadableByteCount((long) value, true);
                }
            });

            holder.chart.getAxisRight().setEnabled(false);

            LineData data = getDataSet(position);
            float ymax = data.getYMax();

            if (mLogScale) {
                yAxis.setAxisMinimum(2f);
                yAxis.setAxisMaximum((float)  Math.ceil(ymax));
                yAxis.setLabelCount((int) (Math.ceil(ymax -2f)));
            } else {
                yAxis.setAxisMinimum(0f);
                yAxis.resetAxisMaximum();
                yAxis.setLabelCount(6);
            }

            if (data.getDataSetByIndex(0).getEntryCount() < 3)
                holder.chart.setData(null);
            else
                holder.chart.setData(data);

            holder.chart.setNoDataText(getString(R.string.notenoughdata));

            holder.chart.invalidate();
            //holder.chart.animateX(750);

            return convertView;
        }


        public void getAverageForGraphList(boolean in, int timeperiod) {



        }

        private LineData getDataSet(int timeperiod) {

            LinkedList<Entry> dataIn = new LinkedList<>();
            LinkedList<Entry> dataOut = new LinkedList<>();

            long interval;

            LinkedList<TrafficHistory.TrafficDatapoint> list;
            switch (timeperiod) {
                case TIME_PERIOD_HOURS:
                    list = VpnStatus.trafficHistory.getHours();
                    interval = 3600 ;
                    break;
                case TIME_PERIOD_MINUTES:
                    list = VpnStatus.trafficHistory.getMinutes();
                    interval = 60;
                    break;
                default:
                    list = VpnStatus.trafficHistory.getSeconds();
                    interval = OpenVPNManagement.mBytecountInterval ;
                    break;
            }
            if (list.size()==0) {
                list = TrafficHistory.getDummyList();
            }

            long firstTimestamp = list.peek().timestamp;
            long lastBytecountIn  = list.peek().in;
            long lastBytecountOut  = list.peek().out;

            long lastts=0;

            for (TrafficHistory.TrafficDatapoint tdp: list){
                float t = (tdp.timestamp - firstTimestamp) / 100f;

                float in = (tdp.in - lastBytecountIn)/ (float) interval;
                float out = (tdp.out - lastBytecountOut) / (float) interval;

                lastBytecountIn = tdp.in;
                lastBytecountOut = tdp.out;

                if (mLogScale) {
                    in = max(2f, (float) Math.log10(in*8));
                    out = max(2f, (float) Math.log10(out* 8));
                }

                if (lastts > 0 && ( tdp.timestamp -lastts> 2 * interval*1000)){
                    dataIn.add(new Entry((lastts- firstTimestamp+ interval)/100f, 0));
                    dataOut.add(new Entry((lastts- firstTimestamp+ interval)/100f, 0));

                    dataIn.add(new Entry(t - interval/100f, 0));
                    dataOut.add(new Entry(t - interval/100f, 0));
                }

                lastts = tdp.timestamp;

                dataIn.add(new Entry(t,in));
                dataOut.add(new Entry(t,out));

            }
            long now = System.currentTimeMillis();
            if (list.peekLast().timestamp < now-interval*1000l) {
                dataIn.add(new Entry((now-firstTimestamp)/100, 0));
                dataOut.add(new Entry((now-firstTimestamp) /100, 0));

                if (now -lastts > 2 * interval*1000) {
                    dataIn.add(new Entry((lastts- firstTimestamp+ interval)/100f, 0));
                    dataOut.add(new Entry((lastts- firstTimestamp+ interval)/100f, 0));
                }
            }

            ArrayList<ILineDataSet> dataSets = new ArrayList<>();


            LineDataSet indata = new LineDataSet(dataIn, "In");
            LineDataSet outdata = new LineDataSet(dataOut, "Out");

            setLineDataAttributes(indata, mColorIn);
            setLineDataAttributes(outdata, mColorOut);

            dataSets.add(indata);
            dataSets.add(outdata);

            return new LineData(dataSets);
        }

        private void setLineDataAttributes(LineDataSet dataSet, int colour) {
            dataSet.setLineWidth(2);
            dataSet.setCircleRadius(1);
            dataSet.setDrawCircles(false);
            dataSet.setDrawFilled(true);
            dataSet.setFillAlpha(42);
            dataSet.setFillColor(colour);
            dataSet.setColor(colour);
            dataSet.setMode(LineDataSet.Mode.LINEAR);

            dataSet.setDrawValues(false);
        }
    }

    private static class ViewHolder {
        LineChart chart;
        TextView title;
    }
}
