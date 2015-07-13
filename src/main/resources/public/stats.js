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
      })
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
        bindTo: '#pie'
      }
    });
  });
};

new window.StatsDashboard();
