# Codey

[![MavenBuild Actions Status](https://github.com/Philtard/Codey/workflows/maven-build/badge.svg)](https://github.com/Philtard/Codey/actions)

Discord bot to compile and run code and fix code formatting, all without leaving discord and just a single click (reaction) away. Supports
tons of languages and automatically finds runnable code in messages.

![Codey Demo](docs/demo.gif)

## discord invite link to invite the bot to your server

paste the link into a browser window and select your server for the bot to join.

https://discord.com/api/oauth2/authorize?client_id=779383631255961640&permissions=11328&scope=bot

## Build and run in docker

* replace `change-me` with the jda token in `docker-compose.yml`
* `docker-compose build && docker-compose up -d`

## run

* replace `change-me` in `application.yml` when running with `spring-boot:run`
  or add the parameter `codey.token` with your bots token to your run configuration in your IDE.
* run the main method in class `CodeyApplication` or the maven task `spring-boot:run`

### How to get a discord token and invite your own bot to your server

* visit and login https://discord.com/developers/applications
* create a `New Application`, give it a name
* click `Bot` and then `Add Bot`
* to reveal your token click `Click To Reveal Token` and copy that token to the places described above depending on how you run it
* select `OAuth2`
* check `bot` in Scopes
* after checking `bot` you can select the bot permissions `Send Messages`, `Add Reactions`, `View Channels`
  and `Manage Messages` (or whatever it is your bot needs if you don't intend to build the one in this repo)
* now click on `Copy` next to the generated link and paste it into a browser window.
* select your server to let the bot join.
* if you run your bot now it shows as online in your servers memberlist.

