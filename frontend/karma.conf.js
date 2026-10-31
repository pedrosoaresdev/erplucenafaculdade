// Karma: inclui um launcher sem sandbox para containers/CI executando como root.
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    plugins: [require('karma-jasmine'), require('karma-chrome-launcher'), require('karma-jasmine-html-reporter'), require('karma-coverage')],
    client: { jasmine: {}, clearContext: false },
    reporters: ['progress'],
    browsers: ['ChromeHeadlessCI'],
    customLaunchers: { ChromeHeadlessCI: { base: 'ChromeHeadless', flags: ['--no-sandbox', '--disable-gpu'] } },
    restartOnFileChange: true,
  });
};
