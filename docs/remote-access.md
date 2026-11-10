# Reach the board from another device

[← README](../README.md)

The board listens on `127.0.0.1:8290` only (`server.address` in `application.yml` and `jagt.yml.dist`).
A phone or a second machine reaches it through your own private network; the network is the guard.

| rule | why |
|------|-----|
| keep `server.address: 127.0.0.1` | jagt adds no token; anything that can connect is let in |
| never bind `0.0.0.0` | it opens the board to every network the Mac is on |
| forward to the loopback port | the private network carries the connection, jagt sees a local one |

The two ways below are examples; any tool that forwards to `127.0.0.1:8290` does the same job.

## Example: a mesh VPN (Tailscale)

Checked 2026-11-10 against [tailscale serve](https://tailscale.com/kb/1242/tailscale-serve).

```sh
tailscale serve --bg --https=443 http://127.0.0.1:8290
tailscale serve status
```

- Open the URL `tailscale serve status` prints, from a device signed in to the same tailnet.
- `--bg` keeps the forward across reboots.
- `tailscale serve` stays inside the tailnet; `tailscale funnel` makes it public, so never use that.
- The CLI changed in Tailscale 1.52; older clients use another syntax.
- Undo: `tailscale serve reset`.

## Example: an SSH local forward

Checked 2026-11-10 against [ssh(1)](https://man.openbsd.org/ssh).

```sh
ssh -N -L 8290:127.0.0.1:8290 <mac>
```

- Run it on the device you browse from; then open `http://127.0.0.1:8290` there.
- The form is `-L [bind_address:]port:host:hostport`; `-N` runs no remote command.
- Leave out `bind_address`, or use `localhost`: an empty one or `*` exposes the forward to the device's own network.
- `<mac>` must accept SSH (Remote Login on macOS).

## Home screen

`index.html` links `manifest.webmanifest` (`display: standalone`, 192 and 512 px icons) and `apple-touch-icon.png`.
Whether a phone offers "Add to Home Screen" is the browser's call; unchecked on a device.
