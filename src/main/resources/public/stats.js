/** The stats dashboard for Analyst */

window.StatsDashboard = function () {
  this.populateFilters();
  this.update();
};

/** populate the filters */
StatsDashboard.prototype.populateFilters = function () {
  var instance = this;
  // get all the possible values
  d3.json('values', function (err, data) {
    d3.select('#commit').selectAll('option.data')
      .data(data.commit)
      .enter().append('option')
      .text(String)
      .attr('value', String);

      d3.select('#instanceType').selectAll('option.data')
        .data(data.instanceType)
        .enter().append('option')
        .text(String)
        .attr('value', String);

      d3.select('#graphId').selectAll('option.data')
        .data(data.graphId)
        .enter().append('option')
        .text(String)
        .attr('value', String);

      d3.select('#jobId').selectAll('option.data')
        .data(data.jobId)
        .enter().append('option')
        .text(String)
        .attr('value', String);

      d3.selectAll('#filters select').on('change', function () {
        instance.update();
      });

      d3.selectAll('#refresh').on('click', function () {
        instance.update();
      });
  });
};

/** update the graphs */
StatsDashboard.prototype.update = function () {
  // build the query string
  var qs = [];

  ['commit', 'instanceType', 'graphId', 'jobId', 'single', 'isochrone'].forEach(function (x) {
    var y = d3.select('#' + x + ' option:checked').attr('value');
    if (y !== null)
      qs.push(x + '=' + y);
  });

  // http://stackoverflow.com/questions/14422198
  d3.select('#charts').selectAll('div').remove();
  d3.select('#charts').append('div').append('h3').text('Makeup of compute time');

  d3.json('stats?' + qs.join('&'), function (err, data) {
    var pie = c3.generate({
      data: {
        columns: [
          ['Initial stop search'].concat(data.map(function (d) { return d.initialStopSearch; })),
          ['Walk search to destination'].concat(data.map(function (d) { return d.walkSearch; })),
          ['Transit search'].concat(data.map(function (d) { return d.initialStopSearch; })),
          ['Propagation'].concat(data.map(function (d) { return d.propagation; })),
          ['Result sets'].concat(data.map(function (d) { return d.resultSets; }))
        ],
        type: 'pie',
      }
    });
    d3.select('#charts').node().appendChild(pie.element);

    // do kernel density estimation for the compute time after applying filters
    var compute = data.map(function (d) { return (d.total - d.graphBuild - d.stopTreeCaching) / 1000; });
    var max = Math.max.apply(this, compute);
    // in 1ms bins
    var x = d3.range(0, max, 0.001);
    var kde = science.stats.kde().sample(compute)(x).map(function (d) { return d[1]; });

    d3.select('#charts').append('div').append('h3').text('Distribution of total compute time (less graph building and stop tree caching)')

    var line = c3.generate({
      data: {
        x: 'x',
        columns: [
          ['x'].concat(x),
          ['Total compute time'].concat(kde)
        ]
      },
      axis: {
        x: {
          label: 'Seconds',

        },
        y: {
          show: false,
          min: 0
        }
      },
      legend: {
        show: false
      }
    });
    d3.select('#charts').node().appendChild(line.element);

    d3.select('#charts').append('div')
      .text('viewing ' + compute.length + ' jobs');
  });
};

new window.StatsDashboard();
