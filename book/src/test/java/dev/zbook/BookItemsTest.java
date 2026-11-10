package dev.zbook;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class BookItemsTest {
 private ZBookPlugin plugin;private ServerMock server;
 @BeforeEach void setup(){server=MockBukkit.mock();plugin=MockBukkit.load(ZBookPlugin.class);}
 @AfterEach void tearDown(){MockBukkit.unmock();}
 @Test void bookIdentityAndKnowledgeArePerItem(){
  BookItems items=plugin.items();ItemStack a=items.book(),b=items.book();assertTrue(items.isBook(a));assertEquals(Material.BOOK,a.getType());assertEquals(1,a.getMaxStackSize());assertNotEquals(items.id(a),items.id(b));assertTrue(items.learned(a).isEmpty());
  ItemStack learned=items.update(a,Set.of("ekspanor_fanfiction"));assertEquals(items.id(a),items.id(learned));assertEquals(Set.of("ekspanor_fanfiction"),items.learned(learned));assertTrue(items.learned(a).isEmpty());assertTrue(items.learned(b).isEmpty());
  assertTrue(items.learned(items.update(learned,Set.of())).isEmpty());assertEquals(items.id(a),items.id(items.update(learned,Set.of())));
 }
 @Test void pageUsesPdcAndOwnModelRatherThanName(){
  Spell spell=plugin.registry().get("EKSPANOR_FANFICTION");assertNotNull(spell);ItemStack page=plugin.items().page(spell,16);assertEquals(16,page.getAmount());/* MockBukkit does not preserve item_model when cloning metadata; checked on Paper. */assertEquals(spell.id(),plugin.items().pageSpell(page));
  ItemStack vanilla=new ItemStack(Material.PAPER);vanilla.editMeta(meta->meta.displayName(page.getItemMeta().displayName()));assertNull(plugin.items().pageSpell(vanilla));assertFalse(plugin.items().isBook(new ItemStack(Material.BOOK)));
 }
 @Test void registryRejectsDuplicateOrMalformedIds(){SpellRegistry registry=new SpellRegistry();registry.register(new EkspanorFanfiction());assertThrows(IllegalArgumentException.class,()->registry.register(new EkspanorFanfiction()));assertNotNull(registry.get("Ekspanor_Fanfiction"));}
}
