/* Copyright 2012 Mobiperf.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mobiperf.chart;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.SimpleSeriesRenderer;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import com.mobiperf.util.Utilities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint.Align;

//import com.mobiperf.lte.Utilities;

/**
 * Average temperature demo chart.
 */
public class CubicChart extends AbstractChart {

  public static double[] index = new double[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 };
  public double[] rtt;
  public double[] tp_down;
  public double[] tp_up;

  public CubicChart(double[] rtt, double[] tp_down, double[] tp_up) {
    this.rtt = new double[index.length];
    this.tp_down = new double[index.length];
    this.tp_up = new double[index.length];

    DecimalFormat df = new DecimalFormat("###.##");

    for (int i = 0; i < index.length; i++) {
      this.rtt[i] = 0;
      this.tp_down[i] = 0;
      this.tp_up[i] = 0;
    }

    for (int i = 1; i <= rtt.length && i <= index.length; i++) {
      this.rtt[index.length - i] = Double.parseDouble(df.format(rtt[rtt.length - i]));
    }

    for (int i = 1; i <= tp_down.length && i <= index.length; i++) {
      this.tp_down[index.length - i] = Double.parseDouble(df
          .format(tp_down[tp_down.length - i] / 1000.0)); // turn into
      // Mbps
    }

    for (int i = 1; i <= tp_up.length && i <= index.length; i++) {
      this.tp_up[index.length - i] = Double
          .parseDouble(df.format(tp_up[tp_up.length - i] / 1000.0)); // turn into
      // Mbps
    }
  }

  /**
   * Returns the chart name.
   * 
   * @return the chart name
   */
  public String getName() {
    return "Average temperature";
  }

  /**
   * Returns the chart description.
   * 
   * @return the chart description
   */
  public String getDesc() {
    return "The average temperature in 4 Greek islands (cubic line chart)";
  }

  public Intent execute(Context context) {
    return null;
  }

  public GraphicalView getGraphView(Context context) {

    double rtt_max = Utilities.getMax(rtt) + 5;
    double tp_down_max = Utilities.getMax(tp_down) + 0.5;
    double tp_up_max = Utilities.getMax(tp_up) + 0.5;
    double tp_max = Math.max(tp_down_max, tp_up_max);
    if (rtt_max > 5000)
      rtt_max = 5000;

    String[] titles = new String[] { "Downlink speed (Mbps)", "Uplink speed (Mbps)" };
    List<double[]> x = new ArrayList<double[]>();
    for (int i = 0; i < titles.length; i++) {
      x.add(index);
    }
    List<double[]> values = new ArrayList<double[]>();
    values.add(tp_down);
    values.add(tp_up);
    int[] colors = new int[] { Color.YELLOW, Color.RED, Color.GREEN };
    PointStyle[] styles = new PointStyle[] { PointStyle.CIRCLE, PointStyle.DIAMOND,
        PointStyle.TRIANGLE };
    XYMultipleSeriesRenderer renderer = new XYMultipleSeriesRenderer(2);
    setRenderer(renderer, colors, styles);
    int length = renderer.getSeriesRendererCount();
    for (int i = 0; i < length; i++) {
      ((XYSeriesRenderer) renderer.getSeriesRendererAt(i)).setFillPoints(true);
    }
    setChartSettings(renderer, "Network Performance", "Experiment NO.", "Throughput (Mbps)", 0.5,
        15.5, 0, tp_max, Color.LTGRAY, Color.LTGRAY);
    renderer.setXLabels(12);
    renderer.setYLabels(10);
    renderer.setShowGrid(true);
    renderer.setXLabelsAlign(Align.RIGHT);
    renderer.setYLabelsAlign(Align.RIGHT);
    renderer.setZoomButtonsVisible(true);
    renderer.setPanLimits(new double[] { -10, 20, -10, 40 });
    renderer.setZoomLimits(new double[] { -10, 20, -10, 40 });

    renderer.setYTitle("Latency (ms)", 1);
    renderer.setYAxisAlign(Align.RIGHT, 1);
    renderer.setYLabelsAlign(Align.LEFT, 1);
    renderer.setYAxisMax(rtt_max, 1);
    renderer.setYAxisMin(0, 1);
    XYMultipleSeriesDataset dataset = buildDataset(titles, x, values);
    values.clear();
    values.add(rtt);
    addXYSeries(dataset, new String[] { "Latency (ms)" }, x, values, 1);

    for (int i = 0; i < length; i++) {
      SimpleSeriesRenderer seriesRenderer = renderer.getSeriesRendererAt(i);
      seriesRenderer.setDisplayChartValues(true);
    }
    GraphicalView graphView = ChartFactory.getCubeLineChartView(context, dataset, renderer, 0.2f);
    return graphView;
  }

}
