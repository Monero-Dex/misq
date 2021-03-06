# Haveno

## What is Haveno?

Haveno is a private and decentralized way to exchange Monero for national currencies or other cryptocurrencies. Haveno uses peer-to-peer networking and multi-signature escrow to facilitate trading without a trusted third party custodian. Disputes can be resolved using non-custodial arbitration. Everything is built around Monero and Tor.

Haveno is the Esperanto word for "Harbor".

## Keep in touch and help out!

We have our own Matrix server. Registrations are not open at the moment, but the rooms are public and can be joined from any matrix client (like Element):

- General discussions: **Haveno** (`#haveno:haveno.network`) relayed on Freenode (`#haveno`)
- Development discussions: **Haveno Development** (`haveno-dev:haveno.network`) relayed on Freenode (`#haveno-dev`)

You can find the rooms by adding the 

## Status of the project

At the moment Haveno is simply a Proof of Concept. It's already possible to initiate a trade, but the project still needs a lot of work before being available for public use.

These are the main priorities for the near future:

- We need a logo. Right now it's a random stock image :)
- The User Interface is basically still Bisq. The entire interface needs to be completely reworked and adapted for Monero as base currency
- Create documentation for developers
- Cleanup the repository from Bisq-specific content
- Organize what Bisq calls "Arbitrators"

See the issue trackers for more detailed info. 

## Why a new platform?

We believe Bisq is not enough for Monero users, which want a private way to exchange Monero for other (crypto)currencies. Haveno is built on Monero, which means all transactions between users are obfuscated by default. Bisq's system is based on Bitcoin and inherits all its design vulnerabilities, for example:

- All Bisq's in-platform transactions are based on Bitcoin, so fully traceable.
- Bisq transactions are unique and easily visible on the blockchain. This means it's trivial to check which Bitcoin transactions are the result of a trade on Bisq.

Even if XMR transactions compose the vast majority of Bisq's activity, the team never displayed much interest in improving the Monero integration. The important privacy issues mentioned above will be solved by simply having Monero as a base currency instead of Bitcoin.

We acknowledge and thank Bisq for their efforts, but we think the Monero community needs a native, private way to exchange XMR for other currencies without passing through Bitcoin first and Haveno is here to fill that gap! We commit to contribute back to Bisq when possible.

## FAQ

See the Wiki on this repository (TODO)

## Running a local test network

1. Download [Monero CLI](https://www.getmonero.org/downloads/) for your system
2. Sync stagenet: `./monerod --stagenet --rpc-login superuser:abctesting123 --confirm-external-bind`, or alternatively, start a local stagenet network:
    * `./monerod --stagenet --no-igd --hide-my-port --data-dir node1 --p2p-bind-ip 127.0.0.1 --p2p-bind-port 48080 --rpc-bind-port 48081 --zmq-rpc-bind-port 48082 --add-exclusive-node 127.0.0.1:38080 --rpc-login superuser:abctesting123 --rpc-access-control-origins http://localhost:8080`<br>
    * `./monerod --stagenet --no-igd --hide-my-port --data-dir node2 --p2p-bind-ip 127.0.0.1 --rpc-bind-ip 0.0.0.0 --confirm-external-bind --add-exclusive-node 127.0.0.1:48080 --rpc-login superuser:abctesting123 --rpc-access-control-origins http://localhost:8080`
3. Download and install [Bitcoin-Qt](https://bitcoin.org/en/download)
4. Run Bitcoin-Qt in regtest mode, e.g.: `/Applications/Bitcoin-Qt.app/Contents/MacOS/Bitcoin-Qt -regtest -peerbloomfilters=1`
5. In Bitcoin-Qt console, mine BTC regtest blocks: `generatetoaddress 101 bcrt1q6j90vywv8x7eyevcnn2tn2wrlg3vsjlsvt46qz`
6. Install [git lfs](https://git-lfs.github.com) for your system<br>
 Ubuntu: `sudo apt install git-lfs`
7. `git clone https://github.com/Haveno-Dex/haveno`
8. Copy monero-wallet-rpc downloaded in step 1 to the haveno project root
9. Apply permission to run monero-wallet-rpc, e.g. `chmod 777 monero-wallet-rpc`
10. Optionally modify [WalletConfig.java](core/src/main/java/bisq/core/btc/setup/WalletConfig.java) with custom settings
11. `cd haveno`
12. `./gradlew build`
13. Start seed node, arbitrator, Alice, and Bob:
    1. `./bisq-seednode --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=2002 --appName=bisq-BTC_REGTEST_Seed_2002 --daoActivated=false`
    2. `./bisq-desktop --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=4444 --appName=bisq-BTC_REGTEST_arbitrator --daoActivated=false`
    3. `./bisq-desktop --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=5555 --appName=bisq-BTC_REGTEST_Alice --daoActivated=false`
    4. `./bisq-desktop --baseCurrencyNetwork=BTC_REGTEST --useLocalhostForP2P=true --useDevPrivilegeKeys=true --nodePort=6666 --appName=bisq-BTC_REGTEST_Bob  --daoActivated=false`
14. Arbitrator window > Account > cmd+n to register a new arbitrator
15. Arbitrator window > Account > cmd+d to register a new mediator
16. Deposit stagenet XMR to Alice and Bob's Haveno wallets (wallet address printed to terminal)
17. When deposited XMR is available, proceed to post offers, etc