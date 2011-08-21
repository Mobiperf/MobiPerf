<script type="text/javascript" src="https://www.google.com/jsapi"></script>
<script type="text/javascript">
  google.load("visualization", "1", {packages:["annotatedtimeline"]});
  google.setOnLoadCallback(drawChart);
  function drawChart() {
    var data = new google.visualization.DataTable();
    data.addColumn('datetime', 'Time');
    data.addColumn('number', 'Remaining Battery');

    {% for row in batterychart_rows %}
    data.addRow({{row}});
    {% endfor %}

    var chart = new google.visualization.AnnotatedTimeLine(
        document.getElementById('battery_chart_div'));
    chart.draw(data, {});
  }
</script>
