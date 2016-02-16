# sunshine<br><br>

An Android app to view the weather details based on location.<br>
This app was built as part of the Udacity course <a href="https://www.udacity.com/course/developing-android-apps--ud853">Developing Android Apps</a><br><br>

This app uses the OpenWeatherMap API. Follow these instructions to get an API key:<br>
1. <a href="http://home.openweathermap.org/">Sign up</a> for the OpenWeatherMap API<br>
2. Generate an API key and add the following lines to your app's build.gradle:<br>
<code>debug { buildConfigField "String", "OPEN_WEATHER_MAP_API_KEY", "\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx5e\"" }</code><br><br>

You can change your location from the settings screen. You can also view the temperature in either celsius (i.e. metric) or farenheit (i.e. imperial) based on your selection in the settings screen. 
