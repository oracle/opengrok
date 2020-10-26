/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2020, Chris Fraire <cfraire@me.com>.
 */
package org.opengrok.indexer.history;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GitTagParserTest {

    private static final String OGK_LOG =
            "2be70f4cce42ddded6d27c4e8a350cae11963e8e:1588842555:tag: 1.3.15:\n" +
            "bff0f6ed1d3e605c8bd7ae862b5ffc1f7cc65abf:1588680440:tag: 1.3.14:\n" +
            "9888808d2ddf3c8cd97b950faca1dc1fd771d93e:1587369044:tag: 1.3.13:\n" +
            "366a4ffc031dfbf0af72f3300ff2ffdb3c413820:1586855339:tag: 1.3.12:\n" +
            "77b66697a02b046a259a7c6620f8ba968bd4d899:1585251795:tag: 1.3.11:\n" +
            "4d863740b66e89cc10572463c53bf83dbe11a2db:1584795619:tag: 1.3.10:\n" +
            "73e2f8d19909113822e5bc6b3d452dcaf798a452:1583748744:tag: 1.3.9:\n" +
            "e26546bcbc37748147a86fd8525c571f2a1e2e15:1581942089:tag: 1.3.8:\n" +
            "6eb8dd46b4f2d0fd90d9aee05e6a8b5f676279f1:1579159381:tag: 1.3.7:\n" +
            "b699311a35e08965f96b6642530dcf50865909e0:1576060799:tag: 1.3.6:\n" +
            "4cb9b46b9a83c1714795f25dc47574e5c5536b91:1575042928:tag: 1.3.5:\n" +
            "1885fb79325b242583d452e5979d6c3e1b9d1b39:1574086243:tag: 1.3.4:\n" +
            "658703f9c8ab4588297bf610975d717315513925:1570700434:tag: 1.3.3:\n" +
            "ba5bb9f91da0ec5e21986a98eb294189a20cd0ad:1568718597:tag: 1.3.2:\n" +
            "07a87999be383fedde1eaa8e4b529275a540864f:1565697340:tag: 1.3.1:\n" +
            "9ce281fd86b06e01343d4f8ab0eb52089a2c4a6d:1564489274:tag: 1.3.0:\n" +
            "a2306f08e4ec32e022891ee630a57a5d2e491936:1564050986:tag: 1.2.25:\n" +
            "39bbbcb2f0a2c86e38ffed912f9c05a07e42b492:1563534636:tag: 1.2.24:\n" +
            "182742d5b8b24d0a98f0f088f820919f84737571:1562077758:tag: 1.2.23:\n" +
            "fafccc276ae2982dcfac02b7c1a14d966a0036bf:1561740481:tag: 1.2.22:\n" +
            "47b057117566a0f0c35e540024358dff84403d25:1561731910:tag: 1.2.21:\n" +
            "768d961ddac3bcce9582d577aef9f64307d60332:1561726211:tag: 1.2.20:\n" +
            "f0fb9b3b7db9a7b4f16ec94984e76d14970eefb4:1561723596:tag: 1.2.19:\n" +
            "66c478d8ae862cbe699ca7ff0342b0978b980385:1561721828:tag: 1.2.18:\n" +
            "15d77e3974d033823cb4e51fa128bc471fd46619:1561238035:tag: 1.2.17:\n" +
            "276951166ffd9da773acd13b3d98f2e54d05992e:1561058629:tag: 1.2.16:\n" +
            "d345c07a3504cc6749565c5070a516c44857ab19:1561018572:tag: 1.2.15:\n" +
            "dd0185f29b0cb7a1f4061902ef08f243a056798b:1560862734:tag: 1.2.14:\n" +
            "f275ac4d5a22aadeff9d5aed0b81edce0ee59558:1560777960:tag: 1.2.13:\n" +
            "15893f51cabb8ea94834b0a28efbe061c4103a25:1560776554:tag: 1.2.12:\n" +
            "f2cbfb44891541e82129c8476c5326b7926b4cf1:1560768450:tag: 1.2.11:\n" +
            "9c68e38bdf5c845e01ae50bd4316dc968640fcb1:1560761852:tag: 1.2.10:\n" +
            "f23ad94568a09e115e8d48c641573dcf2ef7408a:1560172497:tag: 1.2.9:\n" +
            "0bbba9858a56b645d1ffbfd31a1c7d35251e33a0:1558017930:tag: 1.2.8:\n" +
            "32765574b6b47ea0d7755e96e79c8c3695454fcc:1554990465:tag: 1.2.7:\n" +
            "d975631b4733b73d5a1a2e0cde12d2717cab99f3:1553178125:tag: 1.2.6:\n" +
            "380df9ece557c517b33149a832c3e75e7a86a14c:1553068502:tag: 1.2.5:\n" +
            "401db1a4d7c132fbac2b6043ab971dc50df95a53:1552492011:tag: 1.2.4:\n" +
            "cd653449b3024c5a8bd06cab4a7f5b57944c38d3:1551278738:tag: 1.2.3:\n" +
            "17226e651a8a8299e059bfd84705356c89475aa4:1550515017:tag: 1.2.2:\n" +
            "36cb0d33fbf5840c1b5716458448f70c31a6dfba:1549876420:tag: 1.2.1:\n" +
            "968004cc867cc3adf538683fcce2043fd360b5df:1549548370:tag: 1.2.0:\n" +
            "506fa6be49f1bd4dee6b6a60e16f7e7227407d0d:1546627217:tag: 1.1.2:\n" +
            "a59e1e3e239264e8a522dbc2007bc4e8159c3238:1546618232:tag: 1.1.1:\n" +
            "0d4f4d370cd11de277aae2afc72be608b5b634d3:1546526621:tag: 1.1.0:\n" +
            "1d09e4bbdebca0506aee15911e8aa9ea59a62a9c:1544606874:tag: 1.1:\n" +
            "e78ce359940ae71a9977d603120d3aeff4bd4da7:1544466628:tag: 1.1-rc82:\n" +
            "0ed277930eb7b029567363aec49766f910aa2f88:1544300302:tag: 1.1-rc81:\n" +
            "68a610b1ced2bf38b5052add07ae64bc9a55f3e5:1543911950:tag: 1.1-rc80:\n" +
            "aa4476591375d1cf490cd9ed820eb1f64d614e86:1543853081:tag: 1.1-rc79:\n" +
            "e252cac605b11139a9da8a9add7952e70994a1c9:1543831171:tag: 1.1-rc78:\n" +
            "9094486ea0e3eae80e8c1cc27349e3b6b676cf9c:1543827568:tag: 1.1-rc77:\n" +
            "a951cff947e8a7763ffed4a70f5ea24b90a7336b:1543503982:tag: 1.1-rc76:\n" +
            "a9462fe4b910ffe4ff23a2c54d7dbcd018c1e1e7:1542622798:tag: 1.1-rc75:\n" +
            "5e19f944a5df5bc9abd1972d295ac69ce1e92312:1542189179:tag: 1.1-rc74:\n" +
            "d0ba60458679e1b3fe732943ab76c86fcf060076:1542036866:tag: 1.1-rc73:\n" +
            "a5b181fe20cc93c38eff814f786f1451697791f1:1541798638:tag: 1.1-rc72:\n" +
            "77a7e6ae6308cf68bfbb2189e515aadfd0da780b:1541782149:tag: 1.1-rc71:\n" +
            "c1fa2a0f567862f53b8c458e30ac0fa8bdccdc86:1541170041:tag: 1.1-rc70:\n" +
            "0fdb4f337220b9c03c555377e177da1d7aa8d03b:1541060499:tag: 1.1-rc69:\n" +
            "832d397d06b996057de621caaf38187404252575:1540554699:tag: 1.1-rc68:\n" +
            "a754eed4fa802cef0adb13449a569a4dff0febe4:1540547219:tag: 1.1-rc67:\n" +
            "d3905cf2b80d9112a1a4d8c84cd3bf633ecdce83:1540544415:tag: 1.1-rc66:\n" +
            "6c85b9536c7e2a90e32cab2f5a25ed65e8c3c7a4:1540295800:tag: 1.1-rc65:\n" +
            "71e6069421e4b020a8485ed713dc95ed8948ff28:1540200624:tag: 1.1-rc64:\n" +
            "c25ffb49cf2aa5889812159724f6058eb25b8249:1539858193:tag: 1.1-rc63:\n" +
            "22f642dec4a1b2210049dcddaf76262b2cb12c59:1539773895:tag: 1.1-rc62:\n" +
            "88a521fd32dc3cf8b2f99aa2bb157a28b3283e9a:1539768712:tag: 1.1-rc61:\n" +
            "069b2c77aa542343f34018f73d4ebc879b8da2b8:1539608518:tag: 1.1-rc60:\n" +
            "5264cdbad0007b35d5a1aecbdb2552f367d3a5d1:1539271525:tag: 1.1-rc59:\n" +
            "ea3276555c6b40174cb20dc41d15677997f1cc79:1539075417:tag: 1.1-rc58:\n" +
            "921dffe22cc4a4bb785a5bda39697e7583f1aaa4:1539069117:tag: 1.1-rc57:\n" +
            "4bfd6752aac28ddfedd499da60c0e37c6932b638:1539033395:tag: 1.1-rc56:\n" +
            "ad655558f7272dc628d5cd2a4396f406697b5b51:1539016728:tag: 1.1-rc55:\n" +
            "0b8c8842684a06e1d8b4f5b9c329e68101d53ab1:1539001147:tag: 1.1-rc54:\n" +
            "8a1bcdc93c7d77d5b529ffb4b638a229434a3c66:1539000707:tag: 1.1-rc53:\n" +
            "5ab556b6168923c51a64883b89e8d03398efc3da:1539000427:tag: 1.1-rc52:\n" +
            "9bd2a5c19fff03d0efe165ce0fef57b22a74b9c4:1538998433:tag: 1.1-rc51:\n" +
            "bd76f2c39c24d080204419ffeb2153dd095219d7:1538995426:tag: 1.1-rc50:\n" +
            "aac5a4e9fe35eef4ebed7a9bc34bc335cf5254da:1538765605:tag: 1.1-rc49:\n" +
            "66295f1a16a45e42817bfce193a46b65f04a3ab9:1538762679:tag: 1.1-rc48, tag: 1.1-rc47:\n" +
            "3677e50f4cf78ad794b79ab22020d177d47b0eac:1538753208:tag: 1.1-rc46:\n" +
            "6d454cfc137241865a8639a2b1ed64dea81a448b:1538750353:tag: 1.1-rc45, tag: 1.1-rc44:\n" +
            "f3ff7981aa31541724a0a7d81e098b38b322c887:1538656590:tag: 1.1-rc43:\n" +
            "ad1199ac04e31a178e46d097c6fc4123372eb831:1538568781:tag: 1.1-rc42:\n" +
            "fd2e8feba888a0b3c6938be7544aca3502695940:1536324031:tag: 1.1-rc41:\n" +
            "6dd8aa7f90af1b089af127f3bc9a022f85441f08:1536150792:tag: 1.1-rc40:\n" +
            "d690c01f24b18e450d73c33102960f045f78f0f0:1536068116:tag: 1.1-rc39:\n" +
            "a5067ba6eabb7def2d9359af3cd4df978540f185:1534323406:tag: 1.1-rc38:\n" +
            "73d4ef53a88315d7f833ac4ecded8facc1198119:1533903099:tag: 1.1-rc37:\n" +
            "c491b5bc6e1f0dc1b0eca93738b585a75c315221:1533736789:tag: 1.1-rc36:\n" +
            "616b5796a7fc79a77e94ff317bd4bdf0db46d3b6:1532595062:tag: 1.1-rc35:\n" +
            "b1d98e1d412b2776a7605b0e9766aad52fb2d08c:1531992000:tag: 1.1-rc34:\n" +
            "22bab0238ae25c856f8c7710bd21ae7f08e8ba2d:1531231568:tag: 1.1-rc33:\n" +
            "e20a5d890ff58c97e741bf3b128eddc4b8695007:1531214131:tag: 1.1-rc32:\n" +
            "4661bfa7181765acb5ff8a6c34c7ac9d707682f0:1531141150:tag: 1.1-rc31:\n" +
            "4b01e6e7bdeee897684c6efda50aa14c01013693:1529672679:tag: 1.1-rc30:\n" +
            "551221ef813c51423f68232e358db968ba050b9d:1528975520:tag: 1.1-rc29:\n" +
            "dbcad1993409e04b41712fdcf0b2d90534e45c9a:1528276548:tag: 1.1-rc28:\n" +
            "d5581ed33b419405de129d7e1812ae163dde0fea:1527671169:tag: 1.1-rc27:\n" +
            "8ee81fe8fe7a45e5a026d11ac990a25e48b305fd:1525880874:tag: 1.1-rc26:\n" +
            "a04e70f109d566233d6151aca3a62a53ca0b7b1c:1524559975:tag: 1.1-rc25:\n" +
            "58a834bd5836ee0366823c3a2b7f161d1aedd107:1524152471:tag: 1.1-rc24:\n" +
            "176ffdc58f872523c011705ea9fc619ae4c4c065:1523888893:tag: 1.1-rc23:\n" +
            "53b2196178c4ea971de2773ab62bcf51bfbfb572:1522938960:tag: 1.1-rc22:\n" +
            "31a5e1ec372f0269a9b71573a77e6c05dbcd5ce0:1518544917:tag: 1.1-rc21:\n" +
            "4f0537ffcc4815eabe6f4c8884a47f5dcf4773dc:1516972407:tag: 1.1-rc20:\n" +
            "8fca428717b39f8b652bb2d46a217c8f7c579813:1516789919:tag: 1.1-rc19:\n" +
            "312e82d8a2eaac9d941219bdee782253947521c5:1513182808:tag: 1.1-rc18:\n" +
            "e7431f6c86869265ed034b74abc5e3ccf74bcd61:1511173325:tag: 1.1-rc17:\n" +
            "683e5af79422926e5806e290b20e14bef020ee83:1507730597:tag: 1.1-rc16:\n" +
            "2baaa47aee1aab2b5f547a09ff2f822a26f5723b:1506956718:tag: 1.1-rc15:\n" +
            "9d31dcf1deb239534f1daeebe31845b94b1fc1e6:1505722865:tag: 1.1-rc14:\n" +
            "157e93067d475354b88a652998218edbb3efad41:1504036944:tag: 1.1-rc13:\n" +
            "26475a4966b6c35261a7638f16ce5e7a18a8b15d:1503914367:tag: 1.1-rc12:\n" +
            "56bc1b1f8bfafa42e22266b362e1a3be492ea30e:1502281372:tag: 1.1-rc11:\n" +
            "3d76fbcf5827efab8039ddc37ccfa6ef26dafeea:1501751139:tag: 1.1-rc10:\n" +
            "aceba4ff97c06799ec2cf68273f30951aa8ad180:1501662473:tag: 1.1-rc9:\n" +
            "f3a9554fe4a08fae6c34466e5bcb64ce9e1ac8d6:1501169497:tag: 1.1-rc8:\n" +
            "f78e21ffda65b67f80c9bb5be3b448225561e2d8:1500981464:tag: 1.1-rc7:\n" +
            "3eaf29a434f606d6942a910dc5a6c07473bd3637:1500890625:tag: 1.1-rc6:\n" +
            "6fb3bf44e15317cfc1e0a1da886adaced60e348d:1498735563:tag: 1.1-rc5:\n" +
            "b537560f78ce907d49e169c4aeebed3aaf5a120a:1495570173:tag: 1.1-rc4:\n" +
            "9fff1d28e3c0ebbebdd0246491131220269045b2:1494418953:tag: 1.1-rc3:\n" +
            "eb1d0b8de69bdc19b6c1d5db50e6fb386f7fd443:1493989655:tag: 1.1-rc2:\n" +
            "131a4996283810d2ad2cdf42ca5cf0aeb3c69f5b:1493821277:tag: 1.1-rc1:\n" +
            "2850968932c2933b8ebb1f9808991cda0be846ed:1491038723:tag: 1.0:\n" +
            "33d7d01d2dbe72c5d9ccc431c31259ba33706f26:1487945373:tag: 0.13-rc10:\n" +
            "1c66474546b3ca27b8803967e33e9c6d9846f063:1486474858:tag: 0.13-rc9:\n" +
            "741c4ac5d385192d694de45e7da3e6d23b0b0bd1:1485598510:tag: 0.13-rc8:\n" +
            "0be0dfb9fbfa472b031826520354de7a4e178fea:1485263221:tag: 0.13-rc7:\n" +
            "a92452fdda1dc92bfe0edb969f22a290bc933768:1484741212:tag: 0.13.rc6:\n" +
            "b6edf1ff5c2a6c8755bb50637c323712df1702c5:1480942653:tag: 0.13-rc5:\n" +
            "f8419a9bdcfa4f940bfac08a82a0cf1289ed93d0:1476781473:tag: 0.13.rc4:\n" +
            "c7119d5952a97effbc6b5a9779cd1f2bc3e1192e:1476101665:tag: 0.13.rc3:\n" +
            "506e0a1ddf50341a0603af27ecc254ccb72d7dcb:1473681887:tag: 0.12.1.6, origin/0.12-stable:\n" +
            "d305482d0acf552ccd290d6133a52547b8da16be:1427209918:tag: 0.12.1.5:\n" +
            "82e26a688bcd4652544411641c43a5c550d355bf:1422008001:tag: 0.12.1.4:\n" +
            "a999dfc7134de6a0223141aaf5923225a821c7f2:1421423948:tag: 0.12.1.3:\n" +
            "815f08b57f69912f6fe9afe3c91e843a37a8adf5:1421316426:tag: 0.12.1.2:\n" +
            "4b67e8aa5d41fa20f4bab248f1ff04df94f40b8f:1402650166:tag: 0.12.1.1:\n" +
            "e0d03478c01015c1350e11662b5004cdf34ca58b:1468940162:tag: 0.13-rc2:\n" +
            "a36364e104e7472e0b93db94a6d8fa458f005d18:1465375689:tag: 0.13-rc1:\n" +
            "46014450c7a107b2f10690b17559958ddb79f960:1398769525:tag: 0.12.1:\n" +
            "86135a28ef6bba1816deeab0b5a65bd2349afa15:1396963051:tag: 0.12:\n" +
            "653794abf1d9fda5f111e2401d8bd3ead80cfc83:1394030192:tag: 0.12-rc7:\n" +
            "3e0be6bdaa239e1f9f6a95055faf520f2e7c7a86:1388762887:tag: 0.12-rc6:\n" +
            "5900e47e75dd31beacbcfbe39538550233625ece:1387555815:tag: 0.12-rc5:\n" +
            "6ca97f885c9c11acf9a3319b9e87ca3d20bc120a:1386937529:tag: 0.12-rc4:\n" +
            "6bafe4d14b0eb50c353aa9b25365e33b81938328:1386164499:tag: 0.12-rc3:\n" +
            "61172cb46e108f65a356772860ac577141fdf8e8:1385651730:tag: untagged-2d067cc3eab919a1b8d1:\n" +
            "17d2cbd8d550eac92cd13955d63de4298303e4e0:1382628329:tag: 0.12-rc2:\n" +
            "c6963a7ea2753672325502d342e653700be550a8:1377876557:tag: 0.12-rc1:\n" +
            "c23e82b612acd5e947c164114377578116f6d298:1163621273::\n";

    @Test
    public void shouldParseOpenGrokTagsLog() throws IOException {
        final byte[] OGK_LOG_BYTES = OGK_LOG.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream bytesIn = new ByteArrayInputStream(OGK_LOG_BYTES);

        TreeSet<TagEntry> tagList = new TreeSet<>();
        GitTagParser parser = new GitTagParser(tagList);
        parser.processStream(bytesIn);

        long countLF = OGK_LOG.chars().filter(ch -> ch == '\n').count();
        assertEquals("size should == LF count - 1", countLF - 1, tagList.size());

        // c6963a7ea2753672325502d342e653700be550a8:1377876557:tag: 0.12-rc1:
        GitTagEntry t1 = new GitTagEntry("c6963a7ea2753672325502d342e653700be550a8",
                new Date(1377876557_000L), "0.12-rc1");
        assertTrue("should contain entry matching t1", tagList.contains(t1));

        TagEntry floor1 = tagList.floor(t1);
        assertNotNull("should find floor() given contains() is true", floor1);
        assertEquals("tags should equal", t1.tags, floor1.tags);
        assertEquals("hashes should equal", t1.getHash(), ((GitTagEntry) floor1).getHash());

        // 6d454cfc137241865a8639a2b1ed64dea81a448b:1538750353:tag: 1.1-rc45, tag: 1.1-rc44:
        GitTagEntry t2 = new GitTagEntry("6d454cfc137241865a8639a2b1ed64dea81a448b",
                new Date(1538750353_000L), "1.1-rc45, 1.1-rc44");
        assertTrue("should contain entry matching t2", tagList.contains(t2));

        TagEntry floor2 = tagList.floor(t2);
        assertNotNull("should find floor() given contains() is true", floor2);
        assertEquals("tags should equal", t2.tags, floor2.tags);
        assertEquals("hashes should equal", t2.getHash(), ((GitTagEntry) floor2).getHash());
    }

    @Test
    public void shouldParseOneCharacterTags() throws IOException {
        final String LOG =
                "c6963a7ea2753672325502d342e653700be550a8:1377876557:tag: z:\n" +
                "c23e82b612acd5e947c164114377578116f6d298:1163621273::\n";
        final byte[] LOG_BYTES = LOG.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream bytesIn = new ByteArrayInputStream(LOG_BYTES);

        TreeSet<TagEntry> tagList = new TreeSet<>();
        GitTagParser parser = new GitTagParser(tagList);
        parser.processStream(bytesIn);

        long countLF = LOG.chars().filter(ch -> ch == '\n').count();
        assertEquals("size should == LF count - 1", countLF - 1, tagList.size());
    }
}
