package org.Cry_Legende.sHNerfAmethyst;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent; // Changement ici
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ThreadLocalRandom;
// import java.util.logging.Level; // Plus utilisé directement si on enlève les logs custom

public class SHNerfAmethyst extends JavaPlugin implements Listener {

    private int minDrops;
    private int maxDrops;
    private boolean fortuneAffectsAmount;
    private String breakMessage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SHNerfAmethyst a été activé !");
    }

    @Override
    public void onDisable() {
        getLogger().info("SHNerfAmethyst a été désactivé !");
    }

    private void loadConfigValues() {
        minDrops = getConfig().getInt("amethyst-cluster.min-drops", 1);
        maxDrops = getConfig().getInt("amethyst-cluster.max-drops", 1);
        fortuneAffectsAmount = getConfig().getBoolean("amethyst-cluster.fortune-affects-amount", true);
        breakMessage = getConfig().getString("amethyst-cluster.break-message", "");

        if (minDrops < 0) {
            getLogger().warning("min-drops ne peut pas être négatif. Utilisation de 0.");
            minDrops = 0;
        }
        if (maxDrops < minDrops) {
            getLogger().warning("max-drops ne peut pas être inférieur à min-drops. Utilisation de la valeur de min-drops pour max-drops.");
            maxDrops = minDrops;
        }
        if (maxDrops == 0 && minDrops == 0) {
            getLogger().info("min-drops et max-drops sont à 0. Aucun fragment d'améthyste ne sera droppé.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    // HIGH pour s'exécuter AVANT SHTPDrops (qui est à HIGHEST)
    public void onAmethystClusterDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        BlockState blockState = event.getBlockState();

        if (blockState.getType() != Material.AMETHYST_CLUSTER) {
            return;
        }

        // Si le joueur est en créatif, ou si son plugin TP le gère déjà, ne rien faire
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Gérer Silk Touch (Toucher de Soie)
        // Si Silk Touch, on laisse les drops vanilla (le bloc cluster lui-même).
        // SHTPDrops devrait alors le ramasser.
        if (itemInHand.getType() != Material.AIR && itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH)) {
            return;
        }

        // Calculer la quantité à dropper
        int fortuneLevel = 0;
        if (fortuneAffectsAmount && itemInHand.getType() != Material.AIR && itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasEnchant(Enchantment.LOOT_BONUS_BLOCKS)) {
            fortuneLevel = itemInHand.getItemMeta().getEnchantLevel(Enchantment.LOOT_BONUS_BLOCKS);
        }

        int amountToDrop;
        if (minDrops == maxDrops) {
            amountToDrop = minDrops;
        } else {
            // Assurer que maxDrops + 1 ne cause pas d'erreur si maxDrops est Integer.MAX_VALUE, bien que peu probable ici
            amountToDrop = ThreadLocalRandom.current().nextInt(minDrops, maxDrops + 1);
        }

        if (fortuneAffectsAmount && fortuneLevel > 0) {
            amountToDrop += fortuneLevel;
            amountToDrop = Math.min(amountToDrop, maxDrops); // Ne pas dépasser max-drops via fortune
        }

        // S'assurer que la quantité est au moins minDrops et pas plus que maxDrops
        amountToDrop = Math.max(minDrops, Math.min(amountToDrop, maxDrops));


        // Modifier la liste des items droppés
        List<Item> itemsToDrop = event.getItems();

        // On s'attend à ce que l'améthyste ne drop que des AMETHYST_SHARD sans silk touch.
        // On supprime tous les drops d'améthyste existants pour les remplacer par notre quantité.
        ListIterator<Item> iterator = itemsToDrop.listIterator();
        boolean foundOriginalAmethyst = false;
        Item firstAmethystItemEntity = null;

        while (iterator.hasNext()) {
            Item currentItemEntity = iterator.next();
            if (currentItemEntity.getItemStack().getType() == Material.AMETHYST_SHARD) {
                if (!foundOriginalAmethyst) {
                    firstAmethystItemEntity = currentItemEntity; // Garder une référence au premier pour le réutiliser
                    foundOriginalAmethyst = true;
                } else {
                    iterator.remove(); // Supprimer les entités d'améthyste en double/excès
                }
            }
        }

        if (amountToDrop > 0) {
            ItemStack amethystShardsStack = new ItemStack(Material.AMETHYST_SHARD, amountToDrop);
            if (firstAmethystItemEntity != null) {
                // Modifier l'entité Item existante
                firstAmethystItemEntity.setItemStack(amethystShardsStack);
                // Si on a gardé firstAmethystItemEntity et supprimé les autres, la liste contient maintenant
                // seulement firstAmethystItemEntity (modifié).
                // Assurons-nous qu'il n'y a que lui s'il a été trouvé et que nous voulons dropper quelque chose.
                itemsToDrop.clear(); // Nettoyer au cas où il y avait d'autres types d'items (peu probable pour Amethyst Cluster)
                itemsToDrop.add(firstAmethystItemEntity); // Rajouter notre item modifié.
            } else {
                // Si aucun item d'améthyste n'a été trouvé (ce qui serait étrange pour un cluster sans silk touch),
                // et que nous voulons quand même dropper quelque chose (basé sur la config).
                // Normalement, ce cas ne devrait pas arriver si le bloc est bien un AMETHYST_CLUSTER
                // et qu'aucun autre plugin n'a déjà vidé les drops.
                // Dans BlockDropItemEvent, on ne peut pas "créer" une nouvelle entité Item et l'ajouter facilement
                // pour que le serveur la prenne en compte pour le drop naturel.
                // On se contente de modifier ce qui existe. Si rien n'existe, on ne peut rien faire ici.
                // Le plus simple est de supposer qu'il y aura toujours un Item pour les éclats d'améthyste.
                // S'il n'y en a pas, on ne peut pas forcer un drop via cet event sans plus de complexité.
                // Pour l'instant, on assume que firstAmethystItemEntity sera trouvé.
                getLogger().warning("Aucun item d'améthyste initial trouvé pour le cluster cassé par " + player.getName() + ". Impossible de modifier le drop.");
            }

            if (breakMessage != null && !breakMessage.isEmpty() && (firstAmethystItemEntity != null || amountToDrop > 0)) {
                String message = ChatColor.translateAlternateColorCodes('&', breakMessage)
                    .replace("%amount%", String.valueOf(amountToDrop));
                player.sendMessage(message);
            }

        } else { // amountToDrop est 0
            itemsToDrop.clear(); // Ne rien dropper
            if (breakMessage != null && !breakMessage.isEmpty()) {
                String message = ChatColor.translateAlternateColorCodes('&', breakMessage)
                    .replace("%amount%", String.valueOf(0));
                player.sendMessage(message);
            }
        }
        // SHTPDrops (à priorité HIGHEST) va maintenant voir la liste event.getItems() modifiée
        // et prendre ces items pour les mettre dans l'inventaire.
    }
}
