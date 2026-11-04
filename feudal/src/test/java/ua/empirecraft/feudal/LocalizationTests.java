package ua.empirecraft.feudal;

import java.util.*;
import java.util.concurrent.*;
import static ua.empirecraft.feudal.FeudalTests.*;
import static ua.empirecraft.feudal.Database.*;

/** Real catalogs and H2: defaults, isolation, forms, immutable data and old treaty compatibility. */
public final class LocalizationTests {
    public static void main(String[] args)throws Exception {
        count=0;
        test("Default English and nested language scopes restore after exceptions",()->{
            eq(LocalText.tr("Сума"),"Amount");
            try(var ru=LocalText.use("ru")){
                eq(LocalText.tr("Сума"),"Сумма");
                try{try(var uk=LocalText.use("uk")){eq(LocalText.tr("Сума"),"Сума");throw new IllegalArgumentException();}}catch(IllegalArgumentException expected){}
                eq(LocalText.tr("Сума"),"Сумма");
            }
            eq(LocalText.tr("Сума"),"Amount");
        });
        test("Persisted UUID preference controls rendering independently for each player",()->{
            try(var f=new Fixture()){
                var preferences=new CommandPreferences(f.db);new CommandLanguage(null,preferences);
                UUID first=UUID.randomUUID(),second=UUID.randomUUID();preferences.set(first,"ru");
                try(var scope=LocalText.use(first)){eq(LocalText.tr("Назад"),"Назад");eq(CommandLanguage.locale(first),"ru");}
                try(var scope=LocalText.use(second)){eq(LocalText.tr("Назад"),"Back");}
                preferences.set(second,"uk");eq(CommandLanguage.locale(first),"ru");eq(CommandLanguage.locale(second),"uk");
            }finally{CommandLanguage.reset();}
        });
        test("Concurrent rendering cannot leak a player's language",()->{
            var pool=Executors.newFixedThreadPool(2);
            try{
                var ru=pool.submit(()->{try(var scope=LocalText.use("ru")){for(int i=0;i<100;i++)eq(LocalText.tr("Сума"),"Сумма");}return LocalText.tr("Сума");});
                var en=pool.submit(()->{for(int i=0;i<100;i++)eq(LocalText.tr("Сума"),"Amount");return true;});
                eq(ru.get(),"Amount");eq(en.get(),true);
            }finally{pool.shutdownNow();}
        });
        test("Every native form and contract label has English and Russian text",()->{
            for(String language:List.of("en","ru"))try(var scope=LocalText.use(language)){
                for(var form:MarketScreens.FORMS.values()){
                    checkTranslated(form.title());for(String field:form.fields())checkTranslated(field);
                    var shown=form.view();eq(shown.get("id"),form.id());eq(((List<?>)shown.get("fields")).size(),form.fields().size());
                }
                for(String label:ContractTerms.LABELS.values())checkTranslated(label);
                for(var stage:PoliticalModel.Stage.values()){checkTranslated(stage.label);checkTranslated(stage.title);}
            }
        });
        test("Localized choices are accepted without changing the persisted treaty representation",()->{
            for(String[] values:List.of(new String[]{"high","defensive"},new String[]{"высокая","оборона"}))try(var f=new Fixture()){
                String id=f.s.offer(a,"n:B","n:A",0,"",values[0],values[1],0);var row=f.db.one("SELECT * FROM proposals WHERE id=?",id);
                eq(string(row,"autonomy"),"висока");eq(string(row,"military"),"оборона");
            }
            eq(CommandHints.choices("en","висока","обмежена"),List.of("high","limited"));
            eq(CommandHints.choices("ru","добровільно","оборона","повний"),List.of("добровольно","оборона","полный"));
        });
        test("Russian books translate system prose and preserve the author's text and names",()->{
            String author="Внесіть золото /cu reserve deposit gold 64 KRN; Казна Сума";
            var row=new HashMap<String,Object>();row.put("signed_at",1800000000000L);row.put("child_sign",A);row.put("parent_sign",B);
            try(var scope=LocalText.use("ru")){
                var chapters=ContractBook.chapters(27,3,row,new ContractTerms().with("extra",author),"Сума","Володіння",id->id);
                String book=String.join("\n",chapters);eq(book.contains(author),true);eq(book.contains("Вассальный договор №27"),true);
                eq(book.contains("Сума"),true);eq(book.contains("Володіння"),true);eq(book.contains("не выполняются автоматически"),true);
                eq(ContractBook.pages(chapters).stream().allMatch(p->p.length()<=210),true);
            }
        });
        test("Localized help retains monetary amounts, currency codes and positional arguments",()->{
            try(var scope=LocalText.use("ru")){
                eq(LocalText.tr("Баланс: 0.00 ")+"KRN","Баланс: 0.00 KRN");
                eq(Arrays.asList(CommandLanguage.normalize("рынок",new String[]{"договор","Сума","доставить","64"})),List.of("contract","Сума","deliver","64"));
            }
            for(String value:List.of("cancel","скасувати","отмена","отменить"))eq(LocalText.cancel(value),true);
            eq(LocalText.cancel("Name"),false);
        });
        System.out.println("Localization scenarios: "+count);
    }
    static void checkTranslated(String source){if(source.matches(".*[А-Яа-яІіЇїЄєҐґ].*")&&!LocalText.translated(source))throw new AssertionError("Missing catalog value: "+source);}
}
