package me.lovelace.loveclaims.listener;
import me.lovelace.loveclaims.LoveClaims;
import me.lovelace.loveclaims.model.Claim;
import me.lovelace.loveclaims.model.ClaimFlag;
import me.lovelace.loveclaims.model.TrustLevel;
import me.lovelace.loveclans.api.LoveClansAPI;
import me.lovelace.loveclans.model.Clan;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import me.lovelace.loveclaims.util.Notify;
import java.util.Iterator;
import java.util.Optional;

public class ProtectionListener implements Listener {
    private final LoveClaims plugin;

    public ProtectionListener(LoveClaims plugin) {
        this.plugin = plugin;
    }

    private void deny(Player player, Claim claim, Component message) {
        // Denials go through LoveNotify: action bar by default, chat when the bar is off or the text is long.
        // The claim parameter is kept for callers; SILENT_DENY is handled by them.
        Notify.event(player, message);
    }

    // --- PISTONS, LIQUIDS, FIRE: nothing may cross from one claim into another ---

    /** True when something coming from {@code source} would intrude into a different claim {@code target}. */
    static boolean intrudes(java.util.UUID source, java.util.UUID target) {
        return target != null && !target.equals(source);
    }

    private java.util.UUID claimIdAt(org.bukkit.block.Block block) {
        return plugin.getClaimManager().getClaimAt(block.getLocation()).map(Claim::getId).orElse(null);
    }

    private boolean pistonCrosses(org.bukkit.block.Block piston, java.util.List<org.bukkit.block.Block> moved, org.bukkit.block.BlockFace dir) {
        java.util.UUID pistonClaim = claimIdAt(piston);
        // The block directly in front of the piston is the first destination even when nothing is moved.
        if (intrudes(pistonClaim, claimIdAt(piston.getRelative(dir)))) return true;
        for (org.bukkit.block.Block b : moved) {
            if (intrudes(pistonClaim, claimIdAt(b)) || intrudes(pistonClaim, claimIdAt(b.getRelative(dir)))) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonCrosses(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonCrosses(event.getBlock(), event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        org.bukkit.block.Block from = event.getBlock();
        org.bukkit.block.Block to = event.getToBlock();
        if (from.getX() >> 4 == to.getX() >> 4 && from.getZ() >> 4 == to.getZ() >> 4
                && plugin.getClaimManager().getClaimAt(to.getLocation()).isEmpty()) return;
        if (intrudes(claimIdAt(from), claimIdAt(to))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (intrudes(claimIdAt(event.getSource()), claimIdAt(event.getBlock()))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        org.bukkit.block.Block src = event.getIgnitingBlock();
        // Player/entity ignition is handled by the interaction checks; only block-sourced fire is cross-checked.
        if (src == null) return;
        if (intrudes(claimIdAt(src), claimIdAt(event.getBlock()))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        org.bukkit.block.Block src = event.getIgnitingBlock();
        java.util.UUID target = claimIdAt(event.getBlock());
        // Unknown fire source: protect claimed blocks outright.
        if (src == null ? target != null : intrudes(claimIdAt(src), target)) event.setCancelled(true);
    }

    // --- ЗАЩИТА ОТ ВЗРЫВОВ (КРИПЕРЫ / TNT) ---
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(java.util.List<org.bukkit.block.Block> blocks) {
        Iterator<org.bukkit.block.Block> it = blocks.iterator();
        while (it.hasNext()) {
            org.bukkit.block.Block block = it.next();
            Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(block.getLocation());

            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();

                // 1. ЗАЩИТА ЯКОРЯ: Якорь нельзя взорвать никогда (даже если флаг EXPLOSIONS включен)
                org.bukkit.Location anchor = claim.getAnchorLocation();
                if (anchor.getBlockX() == block.getX() && anchor.getBlockY() == block.getY() && anchor.getBlockZ() == block.getZ()) {
                    it.remove();
                    continue;
                }

                // 2. ЗАЩИТА ТЕРРИТОРИИ: Взрывы всегда блокируются на защищённой территории (кроме осад кланов)
                it.remove();
            } else if (isSpawnProtected(block.getLocation())) {
                // Защита спавна от взрывов
                if (!plugin.getConfigManager().getSpawnFlag("explosions")) {
                    it.remove();
                }
            }
        }
    }
    // -----------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();

            // Если приват клановый
            if (claim.isClanTerritory()) {
                // Если приват в режиме осады
                if (claim.isUnderSiege()) {
                    Location anchorLoc = claim.getAnchorLocation();
                    // Разрешаем ломать только баннер (якорь), и только вражескому клану
                    if (event.getBlock().getLocation().equals(anchorLoc)) {
                        LoveClansAPI clansApi = LoveClansAPI.getInstance();
                        boolean isEnemy = false;
                        if (clansApi != null) {
                            Optional<Clan> attackerClanOpt = clansApi.getPlayerClan(player.getUniqueId());
                            Optional<Clan> ownerClanOpt = clansApi.getPlayerClan(claim.getOwnerUuid());
                            if (attackerClanOpt.isPresent() && ownerClanOpt.isPresent()) {
                                isEnemy = clansApi.isAtWar(attackerClanOpt.get(), ownerClanOpt.get());
                            }
                        }

                        if (isEnemy) {
                            event.setCancelled(false);
                        } else {
                            event.setCancelled(true);
                            deny(player, claim, plugin.getConfigManager().getMessage("siege-break-deny"));
                        }
                    } else {
                        event.setCancelled(true);
                        deny(player, claim, plugin.getConfigManager().getMessage("siege-break-deny"));
                    }
                } else {
                    // Если не в осаде, то на клановой территории ломать запрещено всем, без сообщения
                    event.setCancelled(true);
                    // deny(player, claim, plugin.getConfigManager().getMessage("deny-break")); // Убрано сообщение
                }
                return;
            }

            // Обычная логика привата
            if (!claim.hasPermission(player.getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.BUILD)) {
                event.setCancelled(true);
                deny(player, claim, plugin.getConfigManager().getMessage("deny-break"));
            }
            return;
        }

        // Защита спавна
        if (isSpawnProtected(event.getBlock().getLocation())) {
            if (!plugin.getConfigManager().getSpawnFlag("break-blocks")) {
                event.setCancelled(true);
                if (plugin.getConfigManager().getConfig().getBoolean("spawn-claim.say-break-message", false)) {
                    deny(player, null, plugin.getConfigManager().getMessage("deny-break"));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();

            // Если приват клановый
            if (claim.isClanTerritory()) {
                // Если приват в режиме осады
                if (claim.isUnderSiege()) {
                    event.setCancelled(true);
                    deny(player, claim, plugin.getConfigManager().getMessage("siege-place-deny"));
                } else {
                    // Если не в осаде, то на клановой территории ставить запрещено всем, без сообщения
                    event.setCancelled(true);
                    // deny(player, claim, plugin.getConfigManager().getMessage("deny-place")); // Убрано сообщение
                }
                return;
            }

            // Обычная логика привата
            if (!claim.hasPermission(player.getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.BUILD)) {
                event.setCancelled(true);
                deny(player, claim, plugin.getConfigManager().getMessage("deny-place"));
            }
            return;
        }

        // Защита спавна
        if (isSpawnProtected(event.getBlock().getLocation())) {
            if (!plugin.getConfigManager().getSpawnFlag("place-blocks")) {
                event.setCancelled(true);
                if (plugin.getConfigManager().getConfig().getBoolean("spawn-claim.say-break-message", false)) {
                    deny(player, null, plugin.getConfigManager().getMessage("deny-place"));
                }
            }
        }
    }

    // TODO: PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT is deprecated for removal.
    //  There is no direct replacement in the API as of Paper 1.21.11.
    //  This implementation is kept for functionality until a new API is provided.
    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true)
    public void onPearlOrChorus(PlayerTeleportEvent event) {
        if (hasBypass(event.getPlayer())) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL && cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;

        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getTo());
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();
            // Если приват клановый
            if (claim.isClanTerritory()) {
                // Если приват в режиме осады, то эндер-перлы и хорус могут быть разрешены (пока запрещаем)
                if (claim.isUnderSiege()) {
                    event.setCancelled(true);
                    deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("siege-teleport-deny")); // Новое сообщение
                } else {
                    // Если не в осаде, то на клановой территории эндер-перлы и хорус запрещены, без сообщения
                    event.setCancelled(true);
                    // deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("deny-pearl")); // Убрано сообщение
                }
                return;
            }
        }

        if (isSpawnProtected(event.getTo())) {
            if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && !plugin.getConfigManager().getSpawnFlag("enderpearl")) {
                event.setCancelled(true);
                deny(event.getPlayer(), null, plugin.getConfigManager().getMessage("deny-pearl"));
            } else if (cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT && !plugin.getConfigManager().getSpawnFlag("chorus")) {
                event.setCancelled(true);
                deny(event.getPlayer(), null, plugin.getConfigManager().getMessage("deny-chorus"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && event.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        if (hasBypass(event.getPlayer())) return;
        org.bukkit.block.Block block = event.getClickedBlock();
        if (block == null) return;
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();

            // Если приват клановый
            if (claim.isClanTerritory()) {
                // Если приват в режиме осады
                if (claim.isUnderSiege()) {
                    // Если это якорь (баннер), взаимодействовать могут только члены клана-владельца
                    if (block.getLocation().equals(claim.getAnchorLocation())) {
                        LoveClansAPI clansApi = LoveClansAPI.getInstance();
                        boolean isOwnerClanMember = false;
                        if (clansApi != null) {
                            Optional<Clan> ownerClanOpt = clansApi.getPlayerClan(claim.getOwnerUuid());
                            if (ownerClanOpt.isPresent()) {
                                isOwnerClanMember = ownerClanOpt.get().isMember(event.getPlayer().getUniqueId());
                            }
                        }

                        if (!isOwnerClanMember) {
                            event.setCancelled(true);
                            deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("siege-interact-deny"));
                        }
                    } else {
                        event.setCancelled(true);
                        deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("siege-interact-deny"));
                    }
                } else {
                    // Не в осаде: клик по баннеру (якорю) должен обрабатывать LoveClans
                    // (открытие меню клановой территории), поэтому его НЕ отменяем.
                    // Любое другое взаимодействие на клановой территории запрещено, без сообщения.
                    if (!block.getLocation().equals(claim.getAnchorLocation())) {
                        event.setCancelled(true);
                    }
                    // deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("deny-interact")); // Убрано сообщение
                }
                return;
            }

            // Обычная логика привата
            Material type = block.getType();
            if (isContainerBlock(type)) {
                if (!claim.hasPermission(event.getPlayer().getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.CONTAINERS)) {
                    event.setCancelled(true);
                    deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("deny-interact"));
                    return;
                }
            }
            if (isDoorOrButtonBlock(type)) {
                if (!claim.hasPermission(event.getPlayer().getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.INTERACT)) {
                    event.setCancelled(true);
                    deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("deny-interact"));
                    return;
                }
            }
            if (!claim.hasPermission(event.getPlayer().getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.BUILD)) {
                event.setCancelled(true);
                deny(event.getPlayer(), claim, plugin.getConfigManager().getMessage("deny-interact"));
            }
            return;
        }

        // Защита спавна - раньше этой ветки не было вовсе, поэтому двери/люки/кнопки/рычаги и
        // контейнеры на территории спавна (она не входит ни в один Claim, isSpawnProtected()
        // проверяется отдельно) открывались всем без ограничений, хотя точно такое же
        // взаимодействие внутри обычного привата корректно требует прав.
        if (isSpawnProtected(block.getLocation()) && !plugin.getConfigManager().getSpawnFlag("interact")) {
            Material type = block.getType();
            if (isContainerBlock(type) || isDoorOrButtonBlock(type)) {
                event.setCancelled(true);
                if (plugin.getConfigManager().getConfig().getBoolean("spawn-claim.say-break-message", false)) {
                    deny(event.getPlayer(), null, plugin.getConfigManager().getMessage("deny-interact"));
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onElytra(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) return;
        if (hasBypass(player)) return;

        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(player.getLocation());
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();
            // Если приват клановый
            if (claim.isClanTerritory()) {
                // Если приват в режиме осады, то элитры могут быть разрешены (пока запрещаем)
                if (claim.isUnderSiege()) {
                    event.setCancelled(true);
                    deny(player, claim, plugin.getConfigManager().getMessage("siege-elytra-deny")); // Новое сообщение
                } else {
                    // Если не в осаде, то на клановой территории элитры запрещены, без сообщения
                    event.setCancelled(true);
                    // deny(player, claim, plugin.getConfigManager().getMessage("deny-elytra")); // Убрано сообщение
                }
                return;
            }
        }

        if (isSpawnProtected(player.getLocation())) {
            if (!plugin.getConfigManager().getSpawnFlag("elytra")) {
                event.setCancelled(true);
                deny(player, null, plugin.getConfigManager().getMessage("deny-elytra"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            // Стрелы, трезубцы, снежки и т.д. - урон наносит снаряд, но реальный атакующий - стрелок
            attacker = shooter;
        } else {
            return;
        }
        if (attacker.hasPermission("loveclaims.bypass") || attacker.hasPermission("loveclaims.admin")) return;
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(victim.getLocation());
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();

            // Если приват клановый
            if (claim.isClanTerritory()) {
                // PvP всегда разрешено в режиме осады
                if (claim.isUnderSiege()) {
                    event.setCancelled(false);
                } else {
                    // Если не в осаде, то на клановой территории PvP запрещено, без сообщения
                    event.setCancelled(true);
                    // deny(attacker, claim, plugin.getConfigManager().getMessage("deny-pvp")); // Убрано сообщение
                }
                return;
            }

            // Обычная логика привата
            // Если приват в режиме осады, PvP всегда разрешено
            if (claim.isUnderSiege()) {
                event.setCancelled(false);
                return;
            }
            // В обычном привате PvP по умолчанию выключено
            event.setCancelled(true);
        } else if (isSpawnProtected(victim.getLocation()) && !plugin.getConfigManager().getSpawnFlag("pvp")) {
            event.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        org.bukkit.block.Block block = event.getBlockClicked().getRelative(event.getBlockFace());
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();

            if (claim.isClanTerritory()) {
                event.setCancelled(true);
                return;
            }

            if (!claim.hasPermission(player.getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.BUILD)) {
                event.setCancelled(true);
                deny(player, claim, plugin.getConfigManager().getMessage("deny-place"));
            }
            return;
        }

        if (isSpawnProtected(block.getLocation()) && !plugin.getConfigManager().getSpawnFlag("place-blocks")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        org.bukkit.block.Block block = event.getBlockClicked();
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(block.getLocation());
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();

            if (claim.isClanTerritory()) {
                event.setCancelled(true);
                return;
            }

            if (!claim.hasPermission(player.getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.BUILD)) {
                event.setCancelled(true);
                deny(player, claim, plugin.getConfigManager().getMessage("deny-break"));
            }
            return;
        }

        if (isSpawnProtected(block.getLocation()) && !plugin.getConfigManager().getSpawnFlag("break-blocks")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        Player player = null;
        if (remover instanceof Player p) {
            player = p;
        } else if (remover instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            player = shooter;
        }

        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getEntity().getLocation());
        if (claimOpt.isEmpty()) {
            // Раньше здесь был голый return - рамки/картины на территории спавна не входят ни
            // в один Claim (спавн защищается отдельно, через isSpawnProtected()), поэтому эта
            // ветка никогда не доходила до проверки спавна: рамки там можно было свободно
            // ломать. Тот же break-blocks флаг, что и для обычных блоков (см. onBlockBreak).
            if (isSpawnProtected(event.getEntity().getLocation()) && !plugin.getConfigManager().getSpawnFlag("break-blocks")) {
                if (player != null) {
                    if (hasBypass(player)) return;
                    event.setCancelled(true);
                    if (plugin.getConfigManager().getConfig().getBoolean("spawn-claim.say-break-message", false)) {
                        deny(player, null, plugin.getConfigManager().getMessage("deny-break"));
                    }
                } else {
                    event.setCancelled(true);
                }
            }
            return;
        }
        Claim claim = claimOpt.get();

        if (player != null) {
            if (hasBypass(player)) return;

            if (claim.isClanTerritory()) {
                event.setCancelled(true);
                return;
            }

            if (!claim.hasPermission(player.getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.BUILD)) {
                event.setCancelled(true);
                deny(player, claim, plugin.getConfigManager().getMessage("deny-break"));
            }
            return;
        }

        // Не игрок (например, взрыв)
        event.setCancelled(true);
    }

    /**
     * Извлечение/установка/поворот предмета в рамке - отдельное от {@link #onHangingBreak}
     * действие: снятие предмета с занятой рамки не ломает саму рамку и никогда не порождает
     * HangingBreakByEntityEvent, поэтому без этого хендлера было полностью незащищено и внутри
     * приватов, и на спавне (см. javadoc PlayerItemFrameChangeEvent - Paper добавил его именно
     * из-за этого пробела в ванильном Bukkit API).
     */
    @EventHandler(ignoreCancelled = true)
    public void onItemFrameChange(io.papermc.paper.event.player.PlayerItemFrameChangeEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        Location loc = event.getItemFrame().getLocation();
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(loc);
        if (claimOpt.isPresent()) {
            Claim claim = claimOpt.get();

            if (claim.isClanTerritory()) {
                if (claim.isUnderSiege()) {
                    event.setCancelled(true);
                    deny(player, claim, plugin.getConfigManager().getMessage("siege-interact-deny"));
                } else {
                    event.setCancelled(true);
                }
                return;
            }

            // Поворот предмета уже в рамке ничего не удаляет и не добавляет - не требует прав.
            if (event.getAction() == io.papermc.paper.event.player.PlayerItemFrameChangeEvent.ItemFrameChangeAction.ROTATE) {
                return;
            }
            if (!claim.hasPermission(player.getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.CONTAINERS)) {
                event.setCancelled(true);
                deny(player, claim, plugin.getConfigManager().getMessage("deny-interact"));
            }
            return;
        }

        if (!isSpawnProtected(loc)) return;
        io.papermc.paper.event.player.PlayerItemFrameChangeEvent.ItemFrameChangeAction action = event.getAction();
        if (action == io.papermc.paper.event.player.PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE
                && !plugin.getConfigManager().getSpawnFlag("break-blocks")) {
            event.setCancelled(true);
        } else if (action == io.papermc.paper.event.player.PlayerItemFrameChangeEvent.ItemFrameChangeAction.PLACE
                && !plugin.getConfigManager().getSpawnFlag("place-blocks")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Optional<Claim> claimOpt = plugin.getClaimManager().getClaimAt(event.getBlock().getLocation());
        if (claimOpt.isEmpty()) return;
        Claim claim = claimOpt.get();

        if (event.getEntity() instanceof Player player) {
            if (hasBypass(player)) return;

            if (claim.isClanTerritory()) {
                event.setCancelled(true);
                return;
            }

            if (!claim.hasPermission(player.getUniqueId(), me.lovelace.loveclaims.model.ClaimPermission.BUILD)) {
                event.setCancelled(true);
            }
            return;
        }

        // Грифинг со стороны существ (эндермены, зомби ломающие двери и т.д.)
        event.setCancelled(true);
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("loveclaims.bypass")
                || player.hasPermission("loveclaims.admin")
                || player.hasPermission("loveclaims.moderator");
    }

    private boolean isSpawnProtected(org.bukkit.Location loc) {
        return plugin.getConfigManager().isInsideSpawnClaim(loc);
    }

    private boolean isContainerBlock(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST ||
                type == Material.FURNACE || type == Material.BLAST_FURNACE ||
                type == Material.SMOKER || type == Material.BARREL ||
                type.name().contains("SHULKER_BOX") || type == Material.DISPENSER ||
                type == Material.DROPPER || type == Material.HOPPER;
    }

    private boolean isDoorOrButtonBlock(Material type) {
        return type.name().contains("DOOR") || type.name().contains("TRAPDOOR") ||
                type.name().contains("BUTTON") || type == Material.LEVER;
    }
}
